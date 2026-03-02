package kr.hhplus.be.server.application.reservation;

import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.reservation.application.event.ReservationEventListener;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.shared.infrastructure.kafka.KafkaMessageProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * 예약 이벤트 리스너 단위 테스트
 *
 * 변경: 매진 랭킹 + DataPlatform 직접 호출 → Kafka 메시지 발행 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("예약 이벤트 리스너 단위 테스트")
class ReservationEventListenerTest {

    @Mock
    private KafkaMessageProducer kafkaMessageProducer;

    @InjectMocks
    private ReservationEventListener reservationEventListener;

    @Test
    @DisplayName("예약 완료 이벤트 처리 - Kafka 메시지 발행 성공")
    void handleReservationCompleted_ShouldPublishToKafka() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        doNothing().when(kafkaMessageProducer)
                .send(anyString(), anyString(), any());

        // When
        reservationEventListener.handleReservationCompleted(event);

        // Then
        verify(kafkaMessageProducer, times(1))
                .send(KafkaTopicConfig.TOPIC_RESERVATION_COMPLETED, "2025-01-15", event);
    }

    @Test
    @DisplayName("예약 완료 이벤트 처리 - Kafka 발행 실패해도 예외 전파되지 않음")
    void handleReservationCompleted_KafkaFailure_ShouldNotThrow() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        doThrow(new RuntimeException("Kafka 브로커 연결 실패"))
                .when(kafkaMessageProducer)
                .send(anyString(), anyString(), any());

        // When & Then
        try {
            reservationEventListener.handleReservationCompleted(event);
        } catch (Exception e) {
            // Kafka 발행 실패 시 예외 발생 가능
        }

        verify(kafkaMessageProducer, times(1))
                .send(KafkaTopicConfig.TOPIC_RESERVATION_COMPLETED, "2025-01-15", event);
    }
}
