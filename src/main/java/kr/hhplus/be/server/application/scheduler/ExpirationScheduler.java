package kr.hhplus.be.server.application.scheduler;

import kr.hhplus.be.server.domain.concert.SeatRepository;
import kr.hhplus.be.server.domain.queue.QueueToken;
import kr.hhplus.be.server.domain.queue.QueueTokenRepository;
import kr.hhplus.be.server.domain.queue.TokenStatus;
import kr.hhplus.be.server.domain.reservation.ReservationRepository;
import kr.hhplus.be.server.domain.reservation.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 및 토큰 만료 처리 스케줄러
 * 
 * 성능 최적화:
 * - Bulk Update를 사용하여 N+1 문제 해결
 * - 개별 save() 대신 한 번의 쿼리로 처리
 */
@Component
public class ExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);
    
    private static final int MAX_ACTIVE_TOKENS = 100;
    private static final int TOKEN_ACTIVE_MINUTES = 10;

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final QueueTokenRepository queueTokenRepository;

    public ExpirationScheduler(
            ReservationRepository reservationRepository,
            SeatRepository seatRepository,
            QueueTokenRepository queueTokenRepository) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.queueTokenRepository = queueTokenRepository;
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

    /**
     * 대기열 토큰 활성화 (30초마다 실행)
     * - Bulk Update로 성능 최적화
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void activateWaitingTokens() {
        long activeCount = queueTokenRepository.countActive();
        
        if (activeCount >= MAX_ACTIVE_TOKENS) {
            return;
        }
        
        int toActivate = (int) (MAX_ACTIVE_TOKENS - activeCount);
        List<QueueToken> waitingTokens = queueTokenRepository
                .findTopNByStatusOrderByCreatedAtAsc(TokenStatus.WAITING, toActivate);
        
        if (waitingTokens.isEmpty()) {
            return;
        }
        
        // Bulk Update: 대기 토큰 일괄 활성화
        List<Long> tokenIds = waitingTokens.stream()
                .map(QueueToken::getId)
                .toList();
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_ACTIVE_MINUTES);
        int activated = queueTokenRepository.bulkActivate(tokenIds, expiresAt);
        
        log.info("🎫 대기 토큰 {}건 활성화 완료", activated);
        
        // 대기 순서 업데이트 (남은 대기자들)
        updateWaitingPositions();
    }

    /**
     * 만료된 토큰 정리 (5분마다 실행)
     * - Bulk Update로 성능 최적화
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        
        // Bulk Update: 만료된 토큰 일괄 처리
        int expiredCount = queueTokenRepository.bulkExpire(now);
        
        if (expiredCount > 0) {
            log.info("🗑️ 만료된 토큰 {}건 정리 완료", expiredCount);
        }
    }

    /**
     * 대기 순서 재계산
     * - 대기자 수가 많지 않으면 개별 업데이트도 허용
     * - 성능이 중요하면 별도의 Bulk Update 쿼리 추가 가능
     */
    private void updateWaitingPositions() {
        List<QueueToken> waitingTokens = queueTokenRepository
                .findByStatusOrderByCreatedAtAsc(TokenStatus.WAITING);
        
        // 대기자가 100명 미만이면 개별 업데이트 (영속성 컨텍스트 활용)
        int position = 1;
        for (QueueToken token : waitingTokens) {
            token.updatePosition(position++);
        }
        // saveAll로 한 번에 flush
        queueTokenRepository.saveAll(waitingTokens);
    }
}
