package kr.hhplus.be.server.application.reservation;

import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.reservation.application.event.ReservationEventPublisher;
import kr.hhplus.be.server.reservation.application.service.ReservationService;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.shared.common.exception.BusinessException;
import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.concert.domain.model.SeatStatus;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveRequest;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 좌석 예약 유스케이스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("좌석 예약 유스케이스 단위 테스트")
class ReserveSeatUseCaseTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private QueueService queueService;

    @Mock
    private ReservationEventPublisher reservationEventPublisher;

    @InjectMocks
    private ReservationService reserveSeatUseCase;

    private Seat availableSeat;
    private String queueToken;
    private SeatReserveRequest request;

    @BeforeEach
    void setUp() {
        queueToken = "test-queue-token";
        request = new SeatReserveRequest("user123", "2025-01-15", 10);
        availableSeat = new Seat("2025-01-15", 10);
    }

    @Test
    @DisplayName("좌석 예약 성공 - 가용 좌석을 임시 배정한다")
    void execute_Success() {
        // Given
        doNothing().when(queueService).validateToken(queueToken);
        when(seatRepository.findByConcertDateAndSeatNumber(
                request.getDate(), 
                request.getSeatNumber()))
                .thenReturn(Optional.of(availableSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(availableSeat);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // When
        SeatReserveResponse response = reserveSeatUseCase.reserveSeat(request, queueToken);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSeatNumber()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo(SeatReserveResponse.ReservationStatus.TEMP_HELD);
        assertThat(response.getTempHoldExpires()).isNotNull();

        // 좌석이 임시 배정 상태로 변경되었는지 확인
        assertThat(availableSeat.getStatus()).isEqualTo(SeatStatus.TEMP_HELD);
        assertThat(availableSeat.getReservedUserId()).isEqualTo("user123");

        // 검증
        verify(queueService, times(1)).validateToken(queueToken);
        verify(seatRepository, times(1)).findByConcertDateAndSeatNumber(
                request.getDate(), 
                request.getSeatNumber());
        verify(seatRepository, times(1)).save(any(Seat.class));
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(reservationEventPublisher, times(1)).publishReservationCompleted(any(ReservationCompletedEvent.class));
    }

    @Test
    @DisplayName("좌석 예약 실패 - 좌석을 찾을 수 없음")
    void execute_SeatNotFound() {
        // Given
        doNothing().when(queueService).validateToken(queueToken);
        when(seatRepository.findByConcertDateAndSeatNumber(
                request.getDate(), 
                request.getSeatNumber()))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> reserveSeatUseCase.reserveSeat(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("좌석을 찾을 수 없습니다");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("좌석 예약 실패 - 이미 예약된 좌석")
    void execute_SeatAlreadyReserved() throws Exception {
        // Given
        // Reflection으로 상태 변경
        Field statusField = Seat.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(availableSeat, SeatStatus.TEMP_HELD);

        doNothing().when(queueService).validateToken(queueToken);
        when(seatRepository.findByConcertDateAndSeatNumber(
                request.getDate(), 
                request.getSeatNumber()))
                .thenReturn(Optional.of(availableSeat));

        // When & Then
        assertThatThrownBy(() -> reserveSeatUseCase.reserveSeat(request, queueToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예약 가능한 좌석이 아닙니다");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("좌석 예약 실패 - 유효하지 않은 토큰")
    void execute_InvalidToken() {
        // Given
        doThrow(new BusinessException("유효하지 않은 토큰입니다.", "invalid-token", 401))
                .when(queueService).validateToken(queueToken);

        // When & Then
        assertThatThrownBy(() -> reserveSeatUseCase.reserveSeat(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");

        verify(seatRepository, never()).findByConcertDateAndSeatNumber(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 임시 배정 좌석은 해제 후 예약 가능")
    void execute_ExpiredSeatReleased() throws Exception {
        // Given - 만료된 좌석
        Field statusField = Seat.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(availableSeat, SeatStatus.TEMP_HELD);
        
        Field reservedUntilField = Seat.class.getDeclaredField("reservedUntil");
        reservedUntilField.setAccessible(true);
        reservedUntilField.set(availableSeat, LocalDateTime.now().minusMinutes(10));

        doNothing().when(queueService).validateToken(queueToken);
        when(seatRepository.findByConcertDateAndSeatNumber(
                request.getDate(), 
                request.getSeatNumber()))
                .thenReturn(Optional.of(availableSeat));
        when(seatRepository.save(any(Seat.class))).thenReturn(availableSeat);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // When
        SeatReserveResponse response = reserveSeatUseCase.reserveSeat(request, queueToken);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(SeatReserveResponse.ReservationStatus.TEMP_HELD);
    }
}
