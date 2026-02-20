package kr.hhplus.be.server.reservation.application.service;

import kr.hhplus.be.server.shared.common.exception.BusinessException;
import kr.hhplus.be.server.shared.infrastructure.lock.DistributedLock;
import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveRequest;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 예약 서비스 (Application Layer)
 * 도메인 기반 클린 아키텍처
 * 
 * 분산락 적용:
 * - 키: "seat:{date}:{seatNumber}" (좌석 단위)
 * - 범위: 락 획득 → 트랜잭션 → 좌석 예약 → 커밋 → 락 해제
 */
@Service
public class ReservationService {

    private static final Long MOCK_PRICE = 150000L;

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final QueueService queueService;
    private final ConcertRankingService concertRankingService;

    public ReservationService(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            QueueService queueService,
            ConcertRankingService concertRankingService) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.queueService = queueService;
        this.concertRankingService = concertRankingService;
    }

    /**
     * 좌석 예약 (분산락 적용)
     * 
     * 분산락 키: "seat:{date}:{seatNumber}"
     * - 동일한 날짜의 동일한 좌석에 대한 동시 예약을 방지
     * - 다른 좌석에 대한 예약은 병렬 처리 가능
     * 
     * 순서: 락 획득 → @Transactional 시작 → 비즈니스 로직 → 커밋 → 락 해제
     */
    @DistributedLock(key = "'seat:' + #request.date + ':' + #request.seatNumber", waitTime = 5, leaseTime = 5)
    @Transactional
    public SeatReserveResponse reserveSeat(SeatReserveRequest request, String queueToken) {
        // 1. 토큰 검증
        queueService.validateToken(queueToken);

        // 2. 좌석 조회 (분산락이 이미 걸려있으므로 DB 락 불필요)
        Seat seat = seatRepository.findByConcertDateAndSeatNumber(
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

        // 6. 매진 랭킹 업데이트 (Redis Sorted Set)
        concertRankingService.onSeatReserved(request.getDate());

        // 7. 응답 생성
        return new SeatReserveResponse(
                request.getSeatNumber(),
                reservation.getReservedUntil(),
                SeatReserveResponse.ReservationStatus.TEMP_HELD
        );
    }
}
