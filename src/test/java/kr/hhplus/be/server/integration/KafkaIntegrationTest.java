package kr.hhplus.be.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.shared.infrastructure.kafka.KafkaMessageProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kafka 통합 테스트
 *
 * Embedded Kafka를 사용하여 Producer → Kafka → Consumer 전체 흐름을 검증합니다.
 *
 * 검증 사항:
 * 1. 결제 성공 이벤트 Kafka 발행 → Consumer에서 데이터 플랫폼 전송
 * 2. 예약 완료 이벤트 Kafka 발행 → Consumer에서 매진 랭킹 업데이트 + 데이터 플랫폼 전송
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 3,
        topics = {KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS, KafkaTopicConfig.TOPIC_RESERVATION_COMPLETED},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "port=0"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("[통합] Kafka Producer-Consumer 전체 흐름 검증")
class KafkaIntegrationTest {

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }

    @Autowired
    private KafkaMessageProducer kafkaMessageProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DataPlatformSendService dataPlatformSendService;

    @MockitoBean
    private ConcertRankingService concertRankingService;

    @Test
    @DisplayName("결제 성공 이벤트를 Kafka에 발행하면 Consumer에서 데이터 플랫폼에 전송한다")
    void paymentSuccessEvent_ShouldBeConsumedAndSentToDataPlatform() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                1L, 101L, "user123", "2025-01-15", 10, 150000L
        );

        // When - Kafka에 메시지 발행
        kafkaMessageProducer.send(
                KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS,
                event.getUserId(),
                event
        );

        // Then - Consumer에서 데이터 플랫폼 전송이 호출될 때까지 대기
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(dataPlatformSendService, times(1))
                        .sendPaymentData(1L, "user123", "2025-01-15", 10, 150000L)
        );
    }

    @Test
    @DisplayName("예약 완료 이벤트를 Kafka에 발행하면 Consumer에서 랭킹 업데이트 및 데이터 플랫폼에 전송한다")
    void reservationCompletedEvent_ShouldBeConsumedAndProcessed() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        // When - Kafka에 메시지 발행
        kafkaMessageProducer.send(
                KafkaTopicConfig.TOPIC_RESERVATION_COMPLETED,
                event.getConcertDate(),
                event
        );

        // Then - Consumer에서 랭킹 업데이트 + 데이터 플랫폼 전송이 호출될 때까지 대기
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(concertRankingService, times(1)).onSeatReserved("2025-01-15");
            verify(dataPlatformSendService, times(1))
                    .sendReservationData(1L, "user123", "2025-01-15", 10);
        });
    }

    @Test
    @DisplayName("데이터 플랫폼 전송 실패 시에도 Kafka 메시지 소비 자체는 정상 처리된다")
    void kafkaConsumer_DataPlatformFailure_ShouldHandleGracefully() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                2L, 102L, "user456", "2025-02-20", 5, 200000L
        );

        doThrow(new RuntimeException("데이터 플랫폼 연결 실패"))
                .when(dataPlatformSendService)
                .sendPaymentData(anyLong(), anyString(), anyString(), anyInt(), anyLong());

        // When - Kafka에 메시지 발행
        kafkaMessageProducer.send(
                KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS,
                event.getUserId(),
                event
        );

        // Then - Consumer에서 호출 시도는 되지만 예외가 발생함 (재소비 대상)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(dataPlatformSendService, atLeastOnce())
                        .sendPaymentData(2L, "user456", "2025-02-20", 5, 200000L)
        );
    }
}
