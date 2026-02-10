package kr.hhplus.be.server.point.infrastructure.persistence;

import kr.hhplus.be.server.point.domain.model.PointBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
