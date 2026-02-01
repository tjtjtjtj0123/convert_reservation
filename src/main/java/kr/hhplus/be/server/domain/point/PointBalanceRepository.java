package kr.hhplus.be.server.domain.point;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * 포인트 잔액 리포지토리
 */
@Repository
public interface PointBalanceRepository extends JpaRepository<PointBalance, String> {
    
    /**
     * 비관적 락을 사용하여 포인트 잔액 조회
     * - 금액 정합성 보장을 위해 PESSIMISTIC_WRITE 락 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointBalance p WHERE p.userId = :userId")
    Optional<PointBalance> findByUserIdWithLock(@Param("userId") String userId);
}
