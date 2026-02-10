package kr.hhplus.be.server.point.domain.repository;

import kr.hhplus.be.server.point.domain.model.PointBalance;

import java.util.Optional;

/**
 * 포인트 잔액 리포지토리 인터페이스 (Domain Layer)
 * Infrastructure의 구현에 의존하지 않는 순수 인터페이스
 */
public interface PointBalanceRepository {
    
    /**
     * 포인트 잔액 저장
     */
    PointBalance save(PointBalance pointBalance);
    
    /**
     * 사용자 ID로 포인트 잔액 조회
     */
    Optional<PointBalance> findById(String userId);
    
    /**
     * 비관적 락을 사용하여 포인트 잔액 조회
     * - 금액 정합성 보장을 위해 사용
     */
    Optional<PointBalance> findByUserIdWithLock(String userId);
    
    /**
     * 조건부 포인트 차감 (원자적 연산)
     * - 잔액이 충분할 경우에만 차감
     * @return 업데이트된 행 수 (1=성공, 0=실패)
     */
    int deductPointIfSufficient(String userId, Long amount);
}
