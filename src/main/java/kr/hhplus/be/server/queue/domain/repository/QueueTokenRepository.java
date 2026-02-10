package kr.hhplus.be.server.queue.domain.repository;

import kr.hhplus.be.server.queue.domain.model.QueueToken;
import kr.hhplus.be.server.queue.domain.model.TokenStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 대기열 토큰 리포지토리 인터페이스 (Domain Layer)
 * Infrastructure의 구현에 의존하지 않는 순수 인터페이스
 */
public interface QueueTokenRepository {
    
    /**
     * 토큰 저장
     */
    QueueToken save(QueueToken queueToken);
    
    /**
     * 토큰 목록 저장
     */
    List<QueueToken> saveAll(List<QueueToken> queueTokens);
    
    /**
     * 토큰 문자열로 조회
     */
    Optional<QueueToken> findByToken(String token);
    
    /**
     * 사용자 ID로 조회
     */
    Optional<QueueToken> findByUserId(String userId);
    
    /**
     * 대기 중인 토큰 수 조회
     */
    long countWaiting();
    
    /**
     * 활성 토큰 수 조회
     */
    long countActive();
    
    /**
     * 상태별 상위 N개 토큰 조회 (생성 시간 순)
     */
    List<QueueToken> findTopNByStatusOrderByCreatedAtAsc(TokenStatus status, int limit);
    
    /**
     * 만료 시간이 지난 토큰 조회
     */
    List<QueueToken> findByStatusAndExpiresAtBefore(TokenStatus status, LocalDateTime time);
    
    /**
     * 상태별 토큰 조회 (생성 시간 순)
     */
    List<QueueToken> findByStatusOrderByCreatedAtAsc(TokenStatus status);
    
    /**
     * 대기 중인 토큰들을 한 번에 활성화 (Bulk Update)
     */
    int bulkActivate(List<Long> ids, LocalDateTime expiresAt);
    
    /**
     * 만료된 토큰들을 한 번에 만료 처리 (Bulk Update)
     */
    int bulkExpire(LocalDateTime now);
}
