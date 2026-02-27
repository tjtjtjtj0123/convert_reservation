package kr.hhplus.be.server.application.reservation;

import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import kr.hhplus.be.server.reservation.application.event.ReservationEventListener;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * 예약 이벤트 리스너 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("예약 이벤트 리스너 단위 테스트")
class ReservationEventListenerTest {

    @Mock
    private ConcertRankingService concertRankingService;

    @Mock
    private DataPlatformSendService dataPlatformSendService;

    @InjectMocks
    private ReservationEventListener reservationEventListener;

    @Test
    @DisplayName("예약 완료 이벤트 처리 - 랭킹 업데이트 및 데이터 플랫폼 전송")
    void handleReservationCompleted_ShouldUpdateRankingAndSendData() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        doNothing().when(concertRankingService).onSeatReserved("2025-01-15");
        doNothing().when(dataPlatformSendService)
                .sendReservationData(1L, "user123", "2025-01-15", 10);

        // When
        reservationEventListener.handleReservationCompleted(event);

        // Then
        verify(concertRankingService, times(1)).onSeatReserved("2025-01-15");
        verify(dataPlatformSendService, times(1))
                .sendReservationData(1L, "user123", "2025-01-15", 10);
    }

    @Test
    @DisplayName("예약 완료 이벤트 처리 - 랭킹 업데이트 실패해도 데이터 플랫폼 전송은 계속")
    void handleReservationCompleted_RankingFailure_ShouldContinueDataPlatform() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        doThrow(new RuntimeException("Redis 연결 실패"))
                .when(concertRankingService).onSeatReserved("2025-01-15");
        doNothing().when(dataPlatformSendService)
                .sendReservationData(1L, "user123", "2025-01-15", 10);

        // When
        reservationEventListener.handleReservationCompleted(event);

        // Then
        verify(concertRankingService, times(1)).onSeatReserved("2025-01-15");
        verify(dataPlatformSendService, times(1))
                .sendReservationData(1L, "user123", "2025-01-15", 10);
    }

    @Test
    @DisplayName("예약 완료 이벤트 처리 - 데이터 플랫폼 전송 실패해도 예외 발생하지 않음")
    void handleReservationCompleted_DataPlatformFailure_ShouldNotThrow() {
        // Given
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                1L, "user123", "2025-01-15", 10
        );

        doNothing().when(concertRankingService).onSeatReserved("2025-01-15");
        doThrow(new RuntimeException("데이터 플랫폼 연결 실패"))
                .when(dataPlatformSendService)
                .sendReservationData(anyLong(), anyString(), anyString(), anyInt());

        // When
        reservationEventListener.handleReservationCompleted(event);

        // Then
        verify(concertRankingService, times(1)).onSeatReserved("2025-01-15");
        verify(dataPlatformSendService, times(1))
                .sendReservationData(1L, "user123", "2025-01-15", 10);
    }
}
