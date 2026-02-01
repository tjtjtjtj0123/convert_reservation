package kr.hhplus.be.server.application.reservation.usecase;

import kr.hhplus.be.server.application.queue.QueueService;
import kr.hhplus.be.server.common.exception.BusinessException;
import kr.hhplus.be.server.domain.concert.Seat;
import kr.hhplus.be.server.domain.concert.SeatRepository;
import kr.hhplus.be.server.domain.reservation.Reservation;
import kr.hhplus.be.server.domain.reservation.ReservationRepository;
import kr.hhplus.be.server.interfaces.api.reservation.dto.SeatReserveRequest;
import kr.hhplus.be.server.interfaces.api.reservation.dto.SeatReserveResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 좌석 예약 유스케이스 구현 (클린 아키텍처)
 */
@Service
public class ReserveSeatUseCaseImpl implements ReserveSeatUseCase {

    private static final Long MOCK_PRICE = 150000L;

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final QueueService queueService;

    public ReserveSeatUseCaseImpl(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            QueueService queueService) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.queueService = queueService;
    }

    @Override
    @Transactional
    public SeatReserveResponse execute(SeatReserveRequest request, String queueToken) {
        // 1. 토큰 검증
        queueService.validateToken(queueToken);

        // 2. 좌석 조회 및 락 획득
        Seat seat = seatRepository.findByConcertDateAndSeatNumberWithLock(
                        request.getDate(), 
                        request.getSeatNumber())
                .orElseThrow(() -> new BusinessException("좌석을 찾을 수 없습니다.", "seat-not-found", 404));

        // 3. 좌석 예약 가능 여부 확인
        if (seat.isExpired()) {
            seat.release();
        }

        // 4. 좌석 예약 (도메인 로직)
        seat.reserve(request.getUserId(), LocalDateTime.now().plusMinutes(5));
        seatRepository.save(seat);

        // 5. 예약 엔티티 생성
        Reservation reservation = Reservation.create(
                request.getUserId(),
                seat.getId(),
                request.getDate(),
                request.getSeatNumber(),
                MOCK_PRICE
        );
        reservationRepository.save(reservation);

        // 6. 응답 생성
        return new SeatReserveResponse(
                request.getSeatNumber(),
                reservation.getReservedUntil(),
                SeatReserveResponse.ReservationStatus.TEMP_HELD
        );
    }
}
