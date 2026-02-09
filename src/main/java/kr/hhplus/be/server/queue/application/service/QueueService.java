package kr.hhplus.be.server.queue.application.service;

import kr.hhplus.be.server.shared.common.exception.BusinessException;
import kr.hhplus.be.server.queue.domain.model.QueueToken;
import kr.hhplus.be.server.queue.domain.model.TokenStatus;
import kr.hhplus.be.server.queue.domain.repository.QueueTokenRepository;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 대기열 관리 서비스 (Application Layer)
 * 도메인 기반 클린 아키텍처
 */
@Service
@Transactional(readOnly = true)
public class QueueService {

    private static final int MAX_ACTIVE_TOKENS = 100;
    private static final int TOKEN_ACTIVE_MINUTES = 10;

    private final QueueTokenRepository tokenRepository;

    public QueueService(QueueTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 토큰 발급
     */
    @Transactional
    public QueueTokenResponse issueToken(QueueTokenRequest request) {
        // 기존 토큰 확인
        QueueToken existingToken = tokenRepository.findByUserId(request.getUserId())
                .orElse(null);
        
        if (existingToken != null && existingToken.getStatus() != TokenStatus.EXPIRED) {
            return createResponse(existingToken);
        }

        // 새 토큰 생성
        int position = calculatePosition();
        QueueToken token = new QueueToken(request.getUserId(), position);
        
        // 바로 활성화 가능하면 활성화
        if (position == 0) {
            token.activate(LocalDateTime.now().plusMinutes(TOKEN_ACTIVE_MINUTES));
        }
        
        tokenRepository.save(token);
        return createResponse(token);
    }

    /**
     * 토큰 상태 조회
     */
    public QueueTokenResponse getTokenStatus(String tokenValue) {
        QueueToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BusinessException("유효하지 않은 토큰입니다.", "invalid-token", 401));
        
        return createResponse(token);
    }

    /**
     * 토큰 검증 (내부 메서드)
     */
    public void validateToken(String tokenValue) {
        QueueToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BusinessException("유효하지 않은 토큰입니다.", "invalid-token", 401));
        
        if (!token.isActive()) {
            throw new BusinessException("활성화되지 않은 토큰입니다.", "inactive-token", 403);
        }
    }

    /**
     * 토큰 만료 (내부 메서드)
     */
    @Transactional
    public void expireToken(String tokenValue) {
        QueueToken token = tokenRepository.findByToken(tokenValue)
                .orElse(null);
        
        if (token != null) {
            token.expire();
        }
    }

    /**
     * 대기열 위치 계산
     */
    private int calculatePosition() {
        long activeCount = tokenRepository.countActive();
        long waitingCount = tokenRepository.countWaiting();
        
        if (activeCount < MAX_ACTIVE_TOKENS) {
            return 0; // 바로 활성화
        }
        
        return (int) waitingCount + 1;
    }

    /**
     * 응답 DTO 생성
     */
    private QueueTokenResponse createResponse(QueueToken token) {
        int estimatedWaitMinutes = token.getPosition() * 2; // Mock: 1명당 2분
        
        return new QueueTokenResponse(
                token.getToken(),
                token.getPosition(),
                estimatedWaitMinutes * 60 // 초 단위로 변환
        );
    }
}
