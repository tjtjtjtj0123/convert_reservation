package kr.hhplus.be.server.application.payment.usecase;

import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.application.queue.QueueService;
import kr.hhplus.be.server.common.exception.BusinessException;
import kr.hhplus.be.server.domain.concert.Seat;
import kr.hhplus.be.server.domain.concert.SeatRepository;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.reservation.Reservation;
import kr.hhplus.be.server.domain.reservation.ReservationRepository;
import kr.hhplus.be.server.domain.reservation.ReservationStatus;
import kr.hhplus.be.server.interfaces.api.payment.dto.PaymentRequest;
import kr.hhplus.be.server.interfaces.api.payment.dto.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 유스케이스 구현 (클린 아키텍처)
 */
@Service
public class ProcessPaymentUseCaseImpl implements ProcessPaymentUseCase {

    private static final Long MOCK_PRICE = 150000L;

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final PointService pointService;
    private final QueueService queueService;

    public ProcessPaymentUseCaseImpl(
            ReservationRepository reservationRepository,
            SeatRepository seatRepository,
            PaymentRepository paymentRepository,
            PointService pointService,
            QueueService queueService) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.paymentRepository = paymentRepository;
        this.pointService = pointService;
        this.queueService = queueService;
    }

    @Override
    @Transactional
    public PaymentResponse execute(PaymentRequest request, String queueToken) {
        // 1. 토큰 검증
        queueService.validateToken(queueToken);

        // 2. 예약 조회
        Reservation reservation = reservationRepository
                .findByUserIdAndConcertDateAndSeatNumberAndStatus(
                        request.getUserId(),
                        request.getDate(),
                        request.getSeatNumber(),
                        ReservationStatus.TEMP_HELD
                )
                .orElseThrow(() -> new BusinessException(
                        "임시 예약을 찾을 수 없습니다.", 
                        "reservation-not-found", 
                        404
                ));

        // 3. 예약 만료 확인
        if (reservation.isExpired()) {
            throw new BusinessException("예약 시간이 만료되었습니다.", "reservation-expired", 400);
        }

        // 4. 포인트 차감
        pointService.usePoint(request.getUserId(), MOCK_PRICE);

        // 5. 좌석 상태 변경
        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new BusinessException("좌석을 찾을 수 없습니다.", "seat-not-found", 404));
        seat.confirm();
        seatRepository.save(seat);

        // 6. 예약 확정
        reservation.confirm();
        reservationRepository.save(reservation);

        // 7. 결제 내역 생성
        Payment payment = Payment.create(
                reservation.getId(),
                request.getUserId(),
                MOCK_PRICE
        );
        paymentRepository.save(payment);

        // 8. 토큰 만료
        queueService.expireToken(queueToken);

        // 9. 잔액 조회 (응답용)
        Long remainingBalance = pointService.getBalance(request.getUserId()).getBalance().longValue();

        // 10. 응답 생성
        return new PaymentResponse(
                payment.getId().toString(),
                request.getUserId(),
                request.getSeatNumber(),
                MOCK_PRICE.intValue(),
                remainingBalance.intValue(),
                PaymentResponse.PaymentStatus.SUCCESS
        );
    }
}
