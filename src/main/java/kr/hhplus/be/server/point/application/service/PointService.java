package kr.hhplus.be.server.point.application.service;

import kr.hhplus.be.server.shared.common.exception.BusinessException;
import kr.hhplus.be.server.shared.infrastructure.lock.DistributedLock;
import kr.hhplus.be.server.point.domain.model.PointBalance;
import kr.hhplus.be.server.point.domain.repository.PointBalanceRepository;
import kr.hhplus.be.server.point.interfaces.api.dto.PointBalanceResponse;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeRequest;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 관리 서비스 (Application Layer)
 * 도메인 기반 클린 아키텍처
 * 
 * 분산락 적용:
 * - 충전/사용 시 동일 사용자에 대한 동시 요청 제어
 * - 키: "point:{userId}" (사용자 단위)
 * - 충전과 사용이 같은 키를 사용하여 동시 실행 방지
 */
@Service
public class PointService {

    private final PointBalanceRepository pointBalanceRepository;

    public PointService(PointBalanceRepository pointBalanceRepository) {
        this.pointBalanceRepository = pointBalanceRepository;
    }

    /**
     * 포인트 충전 (분산락 적용)
     * 
     * 분산락 키: "point:{userId}"
     * - 동일 사용자의 충전/사용 동시 요청 방지
     */
    @DistributedLock(key = "'point:' + #request.userId", waitTime = 5, leaseTime = 3)
    @Transactional
    public PointChargeResponse charge(PointChargeRequest request) {
        if (request.getAmount() <= 0) {
            throw new BusinessException("충전 금액은 0보다 커야 합니다.", "invalid-amount", 400);
        }

        PointBalance balance = pointBalanceRepository.findById(request.getUserId())
                .orElseGet(() -> new PointBalance(request.getUserId()));
        
        balance.charge(Long.valueOf(request.getAmount()));
        pointBalanceRepository.save(balance);
        
        return new PointChargeResponse(balance.getUserId(), balance.getBalance().intValue());
    }

    /**
     * 포인트 잔액 조회 (락 불필요 - 읽기 전용)
     */
    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(String userId) {
        PointBalance balance = pointBalanceRepository.findById(userId)
                .orElseGet(() -> new PointBalance(userId));
        
        return new PointBalanceResponse(balance.getUserId(), balance.getBalance().intValue());
    }

    /**
     * 포인트 사용 (분산락 적용)
     * 
     * 분산락 키: "point:{userId}"
     * - 동일 사용자의 충전/사용 동시 요청 방지
     * - DB 비관적 락 대신 Redis 분산락으로 DB 부하 최소화
     */
    @DistributedLock(key = "'point:' + #userId", waitTime = 5, leaseTime = 3)
    @Transactional
    public void usePoint(String userId, Long amount) {
        PointBalance balance = pointBalanceRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", "user-not-found", 404));
        
        if (!balance.hasEnoughBalance(amount)) {
            throw new BusinessException("잔액이 부족합니다.", "insufficient-balance", 400);
        }
        
        balance.use(amount);
        pointBalanceRepository.save(balance);
    }
}
