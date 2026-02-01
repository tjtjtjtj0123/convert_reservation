package kr.hhplus.be.server.application.payment;

import kr.hhplus.be.server.application.payment.usecase.ProcessPaymentUseCaseImpl;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.application.queue.QueueService;
import kr.hhplus.be.server.common.exception.BusinessException;
import kr.hhplus.be.server.domain.concert.Seat;
import kr.hhplus.be.server.domain.concert.SeatRepository;
import kr.hhplus.be.server.domain.concert.SeatStatus;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.reservation.Reservation;
import kr.hhplus.be.server.domain.reservation.ReservationRepository;
import kr.hhplus.be.server.domain.reservation.ReservationStatus;
import kr.hhplus.be.server.interfaces.api.payment.dto.PaymentRequest;
import kr.hhplus.be.server.interfaces.api.payment.dto.PaymentResponse;
import kr.hhplus.be.server.interfaces.api.point.dto.PointBalanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 결제 유스케이스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("결제 유스케이스 단위 테스트")
class ProcessPaymentUseCaseTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PointService pointService;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private ProcessPaymentUseCaseImpl processPaymentUseCase;

    private PaymentRequest request;
    private Reservation reservation;
    private Seat seat;
    private String queueToken;

    @BeforeEach
    void setUp() throws Exception {
        queueToken = "test-queue-token";
        request = new PaymentRequest("user123", 10, "2025-01-15");
        
        // 예약 생성
        reservation = Reservation.create("user123", 1L, "2025-01-15", 10, 150000L);
        setFieldValue(reservation, "id", 101L);

        // 좌석 생성 (임시 배정 상태)
        seat = new Seat("2025-01-15", 10);
        setFieldValue(seat, "id", 1L);
        setFieldValue(seat, "status", SeatStatus.TEMP_HELD);
        setFieldValue(seat, "reservedUserId", "user123");
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("결제 성공 - 예약 확정 및 포인트 차감")
    void execute_Success() throws Exception {
        // Given
        doNothing().when(queueService).validateToken(queueToken);
        when(reservationRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
                request.getUserId(),
                request.getDate(),
                request.getSeatNumber(),
                ReservationStatus.TEMP_HELD
        )).thenReturn(Optional.of(reservation));
        when(seatRepository.findById(reservation.getSeatId())).thenReturn(Optional.of(seat));
        when(seatRepository.save(any(Seat.class))).thenReturn(seat);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            setFieldValue(payment, "id", 201L);
            return payment;
        });
        doNothing().when(pointService).usePoint("user123", 150000L);
        doNothing().when(queueService).expireToken(queueToken);
        when(pointService.getBalance("user123")).thenReturn(new PointBalanceResponse("user123", 350000));

        // When
        PaymentResponse response = processPaymentUseCase.execute(request, queueToken);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo("201");
        assertThat(response.getUserId()).isEqualTo("user123");
        assertThat(response.getSeatNumber()).isEqualTo(10);
        assertThat(response.getAmount()).isEqualTo(150000);
        assertThat(response.getRemainingPoints()).isEqualTo(350000);
        assertThat(response.getStatus()).isEqualTo(PaymentResponse.PaymentStatus.SUCCESS);

        // 좌석이 예약 확정 상태인지 확인
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        // 검증
        verify(queueService, times(1)).validateToken(queueToken);
        verify(pointService, times(1)).usePoint("user123", 150000L);
        verify(queueService, times(1)).expireToken(queueToken);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 실패 - 예약을 찾을 수 없음")
    void execute_ReservationNotFound() {
        // Given
        doNothing().when(queueService).validateToken(queueToken);
        when(reservationRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
                request.getUserId(),
                request.getDate(),
                request.getSeatNumber(),
                ReservationStatus.TEMP_HELD
        )).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("임시 예약을 찾을 수 없습니다");

        verify(paymentRepository, never()).save(any());
        verify(pointService, never()).usePoint(any(), anyLong());
    }

    @Test
    @DisplayName("결제 실패 - 예약 시간 만료")
    void execute_ReservationExpired() throws Exception {
        // Given - 만료된 예약
        setFieldValue(reservation, "reservedUntil", java.time.LocalDateTime.now().minusMinutes(10));
        
        doNothing().when(queueService).validateToken(queueToken);
        when(reservationRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
                request.getUserId(),
                request.getDate(),
                request.getSeatNumber(),
                ReservationStatus.TEMP_HELD
        )).thenReturn(Optional.of(reservation));

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("예약 시간이 만료되었습니다");

        verify(paymentRepository, never()).save(any());
        verify(pointService, never()).usePoint(any(), anyLong());
    }

    @Test
    @DisplayName("결제 실패 - 유효하지 않은 토큰")
    void execute_InvalidToken() {
        // Given
        doThrow(new BusinessException("유효하지 않은 토큰입니다.", "invalid-token", 401))
                .when(queueService).validateToken(queueToken);

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");

        verify(reservationRepository, never()).findByUserIdAndConcertDateAndSeatNumberAndStatus(
                any(), any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패 - 포인트 부족")
    void execute_InsufficientPoints() throws Exception {
        // Given
        doNothing().when(queueService).validateToken(queueToken);
        when(reservationRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
                request.getUserId(),
                request.getDate(),
                request.getSeatNumber(),
                ReservationStatus.TEMP_HELD
        )).thenReturn(Optional.of(reservation));
        doThrow(new BusinessException("포인트가 부족합니다.", "insufficient-points", 400))
                .when(pointService).usePoint("user123", 150000L);

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("포인트가 부족합니다");

        verify(paymentRepository, never()).save(any());
        verify(queueService, never()).expireToken(any());
    }

    @Test
    @DisplayName("결제 실패 - 좌석을 찾을 수 없음")
    void execute_SeatNotFound() {
        // Given
        doNothing().when(queueService).validateToken(queueToken);
        when(reservationRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
                request.getUserId(),
                request.getDate(),
                request.getSeatNumber(),
                ReservationStatus.TEMP_HELD
        )).thenReturn(Optional.of(reservation));
        doNothing().when(pointService).usePoint("user123", 150000L);
        when(seatRepository.findById(reservation.getSeatId())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> processPaymentUseCase.execute(request, queueToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("좌석을 찾을 수 없습니다");

        verify(paymentRepository, never()).save(any());
    }
}
