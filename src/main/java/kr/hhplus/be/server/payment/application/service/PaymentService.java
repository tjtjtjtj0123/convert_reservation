package kr.hhplus.be.server.payment.application.service;

import kr.hhplus.be.server.point.application.service.PointService;
import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.shared.common.exception.BusinessException;
import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import kr.hhplus.be.server.payment.interfaces.api.dto.PaymentRequest;
import kr.hhplus.be.server.payment.interfaces.api.dto.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 서비스 (Application Layer)
 * 도메인 기반 클린 아키텍처
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Long MOCK_PRICE = 150000L;

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final PointService pointService;
    private final QueueService queueService;

    public PaymentService(
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

    /**
     * 결제 처리
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String queueToken) {
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
        Payment payment = new Payment(
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
