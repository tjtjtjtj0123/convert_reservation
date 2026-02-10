package kr.hhplus.be.server.queue.infrastructure.persistence;

import kr.hhplus.be.server.queue.domain.model.QueueToken;
import kr.hhplus.be.server.queue.domain.model.TokenStatus;
import kr.hhplus.be.server.queue.domain.repository.QueueTokenRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 대기열 토큰 리포지토리 구현체 (Infrastructure Layer)
 * Domain의 QueueTokenRepository를 JPA로 구현
 */
@Repository
@Transactional(readOnly = true)
public class QueueTokenRepositoryImpl implements QueueTokenRepository {
    
    private final QueueTokenJpaRepository jpaRepository;

    public QueueTokenRepositoryImpl(QueueTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public QueueToken save(QueueToken queueToken) {
        return jpaRepository.save(queueToken);
    }

    @Override
    @Transactional
    public List<QueueToken> saveAll(List<QueueToken> queueTokens) {
        return jpaRepository.saveAll(queueTokens);
    }

    @Override
    public Optional<QueueToken> findByToken(String token) {
        return jpaRepository.findByToken(token);
    }

    @Override
    public Optional<QueueToken> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public long countWaiting() {
        return jpaRepository.countWaiting();
    }

    @Override
    public long countActive() {
        return jpaRepository.countActive();
    }

    @Override
    public List<QueueToken> findTopNByStatusOrderByCreatedAtAsc(TokenStatus status, int limit) {
        return jpaRepository.findTopNByStatusOrderByCreatedAtAsc(status, Pageable.ofSize(limit));
    }

    @Override
    public List<QueueToken> findByStatusAndExpiresAtBefore(TokenStatus status, LocalDateTime time) {
        return jpaRepository.findByStatusAndExpiresAtBefore(status, time);
    }

    @Override
    public List<QueueToken> findByStatusOrderByCreatedAtAsc(TokenStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Override
    @Transactional
    public int bulkActivate(List<Long> ids, LocalDateTime expiresAt) {
        return jpaRepository.bulkActivate(ids, expiresAt);
    }

    @Override
    @Transactional
    public int bulkExpire(LocalDateTime now) {
        return jpaRepository.bulkExpire(now);
    }
}
