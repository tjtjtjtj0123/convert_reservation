package kr.hhplus.be.server.point.application.service;

import kr.hhplus.be.server.shared.common.exception.BusinessException;
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
 */
@Service
@Transactional(readOnly = true)
public class PointService {

    private final PointBalanceRepository pointBalanceRepository;

    public PointService(PointBalanceRepository pointBalanceRepository) {
        this.pointBalanceRepository = pointBalanceRepository;
    }

    /**
     * 포인트 충전
     */
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
     * 포인트 잔액 조회
     */
    public PointBalanceResponse getBalance(String userId) {
        PointBalance balance = pointBalanceRepository.findById(userId)
                .orElseGet(() -> new PointBalance(userId));
        
        return new PointBalanceResponse(balance.getUserId(), balance.getBalance().intValue());
    }

    /**
     * 포인트 사용 (내부 메서드)
     */
    @Transactional
    public void usePoint(String userId, Long amount) {
        PointBalance balance = pointBalanceRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", "user-not-found", 404));
        
        if (!balance.hasEnoughBalance(amount)) {
            throw new BusinessException("잔액이 부족합니다.", "insufficient-balance", 400);
        }
        
        balance.use(amount);
    }
}
