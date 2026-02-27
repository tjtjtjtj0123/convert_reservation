package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.payment.application.event.PaymentEventPublisher;
import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import kr.hhplus.be.server.reservation.application.event.ReservationEventPublisher;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 이벤트 기반 트랜잭션 분리 통합 테스트
 *
 * @TransactionalEventListener + @Async 동작을 검증합니다.
 * - 이벤트 발행 후 비동기로 리스너가 호출되는지 확인
 * - DataPlatformSendService Mock 호출 검증
 */
@DisplayName("[통합] 이벤트 기반 트랜잭션 분리 검증")
class EventDrivenIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @Autowired
    private ReservationEventPublisher reservationEventPublisher;

    @MockitoBean
    private DataPlatformSendService dataPlatformSendService;

    @MockitoBean
    private ConcertRankingService concertRankingService;

    @Test
    @DisplayName("결제 성공 이벤트 발행 시 데이터 플랫폼으로 결제 데이터가 비동기 전송된다")
    void paymentSuccessEvent_ShouldSendDataToDataPlatform() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                1L, 101L, "user123", "2025-01-15", 10, 150000L
        );

        // When - 이벤트 발행 (트랜잭션 외부에서 직접 발행하므로 즉시 처리됨)
        paymentEventPublisher.publishPaymentSuccess(event);

        // Then - 비동기이므로 awaitility로 대기
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                verify(dataPlatformSendService, times(1))
                        .sendPaymentData(1L, "user123", "2025-01-15", 10, 150000L)
        );
    }

    @Test
    @DisplayName("예약 완료 이벤트 발행 시 랭킹 업데이트 및 데이터 플랫폼 전송이 비동기로 수행된다")
    void reservationCompletedEvent_ShouldUpdateRankingAndSendData() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        // When
        reservationEventPublisher.publishReservationCompleted(event);

        // Then - 비동기이므로 awaitility로 대기
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(concertRankingService, times(1)).onSeatReserved("2025-01-15");
            verify(dataPlatformSendService, times(1))
                    .sendReservationData(1L, "user123", "2025-01-15", 10);
        });
    }

    @Test
    @DisplayName("데이터 플랫폼 전송 실패 시에도 이벤트 처리가 정상 완료된다")
    void dataPlatformFailure_ShouldNotAffectEventProcessing() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                2L, 102L, "user456", "2025-02-20", 5, 200000L
        );

        doThrow(new RuntimeException("데이터 플랫폼 연결 실패"))
                .when(dataPlatformSendService)
                .sendPaymentData(anyLong(), anyString(), anyString(), anyInt(), anyLong());

        // When - 예외가 발생해도 이벤트 처리가 중단되지 않아야 함
        paymentEventPublisher.publishPaymentSuccess(event);

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                verify(dataPlatformSendService, times(1))
                        .sendPaymentData(2L, "user456", "2025-02-20", 5, 200000L)
        );
    }
}
