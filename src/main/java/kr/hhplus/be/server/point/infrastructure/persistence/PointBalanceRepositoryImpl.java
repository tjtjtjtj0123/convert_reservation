package kr.hhplus.be.server.point.infrastructure.persistence;

import kr.hhplus.be.server.point.domain.model.PointBalance;
import kr.hhplus.be.server.point.domain.repository.PointBalanceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 포인트 잔액 리포지토리 구현체 (Infrastructure Layer)
 * Domain의 PointBalanceRepository를 JPA로 구현
 */
@Repository
@Transactional(readOnly = true)
public class PointBalanceRepositoryImpl implements PointBalanceRepository {

    private final PointBalanceJpaRepository jpaRepository;

    public PointBalanceRepositoryImpl(PointBalanceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public PointBalance save(PointBalance pointBalance) {
        return jpaRepository.save(pointBalance);
    }

    @Override
    public Optional<PointBalance> findById(String userId) {
        return jpaRepository.findById(userId);
    }

    @Override
    public Optional<PointBalance> findByUserIdWithLock(String userId) {
        return jpaRepository.findByUserIdWithLock(userId);
    }

    @Override
    @Transactional
    public int deductPointIfSufficient(String userId, Long amount) {
        return jpaRepository.deductPointIfSufficient(userId, amount);
    }
}
