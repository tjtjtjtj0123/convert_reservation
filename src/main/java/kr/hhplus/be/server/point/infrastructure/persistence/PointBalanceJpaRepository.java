package kr.hhplus.be.server.point.infrastructure.persistence;

import kr.hhplus.be.server.point.domain.model.PointBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * 포인트 잔액 JPA Repository (Infrastructure Layer)
 */
interface PointBalanceJpaRepository extends JpaRepository<PointBalance, String> {
    
    /**
     * 비관적 락을 사용하여 포인트 잔액 조회
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pb FROM PointBalance pb WHERE pb.userId = :userId")
    Optional<PointBalance> findByUserIdWithLock(@Param("userId") String userId);
    
    /**
     * 조건부 포인트 차감 (잔액이 충분한 경우에만 차감)
     * - 원자적 연산으로 Race Condition 방지
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PointBalance pb SET pb.balance = pb.balance - :amount " +
           "WHERE pb.userId = :userId AND pb.balance >= :amount")
    int deductPointIfSufficient(@Param("userId") String userId, @Param("amount") Long amount);
}
