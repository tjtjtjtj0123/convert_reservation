package kr.hhplus.be.server.domain.queue;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 대기열 토큰 리포지토리
 */
@Repository
public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {
    
    Optional<QueueToken> findByToken(String token);
    
    Optional<QueueToken> findByUserId(String userId);
    
    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.status = 'WAITING'")
    long countWaiting();
    
    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.status = 'ACTIVE'")
    long countActive();
    
    @Query("SELECT q FROM QueueToken q WHERE q.status = :status ORDER BY q.createdAt ASC")
    List<QueueToken> findTopNByStatusOrderByCreatedAtAsc(@Param("status") TokenStatus status, Pageable pageable);
    
    default List<QueueToken> findTopNByStatusOrderByCreatedAtAsc(TokenStatus status, int limit) {
        return findTopNByStatusOrderByCreatedAtAsc(status, Pageable.ofSize(limit));
    }
    
    List<QueueToken> findByStatusAndExpiresAtBefore(TokenStatus status, LocalDateTime time);
    
    List<QueueToken> findByStatusOrderByCreatedAtAsc(TokenStatus status);
    
    /**
     * 대기 중인 토큰들을 한 번에 활성화 (Bulk Update)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE QueueToken q SET q.status = 'ACTIVE', q.expiresAt = :expiresAt, q.position = 0 " +
           "WHERE q.id IN :ids")
    int bulkActivate(@Param("ids") List<Long> ids, @Param("expiresAt") LocalDateTime expiresAt);
    
    /**
     * 만료된 토큰들을 한 번에 만료 처리 (Bulk Update)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE QueueToken q SET q.status = 'EXPIRED' " +
           "WHERE q.status = 'ACTIVE' AND q.expiresAt < :now")
    int bulkExpire(@Param("now") LocalDateTime now);
}
