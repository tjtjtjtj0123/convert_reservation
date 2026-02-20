package kr.hhplus.be.server.shared.infrastructure.scheduler;

import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 만료 처리 스케줄러
 * 
 * 대기열 관련 스케줄러는 RedisQueueScheduler로 이전됨.
 * 이 스케줄러는 임시 예약 만료 처리만 담당.
 */
@Component
public class ExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    public ExpirationScheduler(
            ReservationRepository reservationRepository,
            SeatRepository seatRepository) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
    }

    /**
     * 만료된 임시 예약 해제 (1분마다 실행)
     * - Bulk Update로 성능 최적화
     * - 예약 만료 + 좌석 해제를 각각 한 번의 쿼리로 처리
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 만료된 예약의 좌석 ID 목록 조회
        List<Long> expiredSeatIds = reservationRepository
                .findSeatIdsByStatusAndReservedUntilBefore(ReservationStatus.TEMP_HELD, now);
        
        if (expiredSeatIds.isEmpty()) {
            return;
        }
        
        // 2. Bulk Update: 좌석 상태 일괄 해제
        int releasedSeats = seatRepository.bulkRelease(expiredSeatIds);
        
        // 3. Bulk Update: 예약 상태 일괄 만료 처리
        int expiredReservations = reservationRepository.bulkExpire(now);
        
        log.info("⏰ 만료된 예약 {}건, 좌석 {}건 해제 완료", expiredReservations, releasedSeats);
    }
}
