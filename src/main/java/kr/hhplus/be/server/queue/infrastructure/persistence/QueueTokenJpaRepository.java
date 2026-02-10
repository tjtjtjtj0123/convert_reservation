package kr.hhplus.be.server.queue.infrastructure.persistence;

import kr.hhplus.be.server.queue.domain.model.QueueToken;
import kr.hhplus.be.server.queue.domain.model.TokenStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 대기열 토큰 JPA Repository (Infrastructure Layer)
 */
interface QueueTokenJpaRepository extends JpaRepository<QueueToken, Long> {
    
    Optional<QueueToken> findByToken(String token);
    
    Optional<QueueToken> findByUserId(String userId);
    
    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.status = 'WAITING'")
    long countWaiting();
    
    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.status = 'ACTIVE'")
    long countActive();
    
    @Query("SELECT q FROM QueueToken q WHERE q.status = :status ORDER BY q.createdAt ASC")
    List<QueueToken> findTopNByStatusOrderByCreatedAtAsc(@Param("status") TokenStatus status, Pageable pageable);
    
    List<QueueToken> findByStatusAndExpiresAtBefore(TokenStatus status, LocalDateTime time);
    
    List<QueueToken> findByStatusOrderByCreatedAtAsc(TokenStatus status);
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE QueueToken q SET q.status = 'ACTIVE', q.expiresAt = :expiresAt, q.position = 0 " +
           "WHERE q.id IN :ids")
    int bulkActivate(@Param("ids") List<Long> ids, @Param("expiresAt") LocalDateTime expiresAt);
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE QueueToken q SET q.status = 'EXPIRED' " +
           "WHERE q.status = 'ACTIVE' AND q.expiresAt < :now")
    int bulkExpire(@Param("now") LocalDateTime now);
}
