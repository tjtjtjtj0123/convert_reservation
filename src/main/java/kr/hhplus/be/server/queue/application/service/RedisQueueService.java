package kr.hhplus.be.server.queue.application.service;

import kr.hhplus.be.server.queue.domain.repository.RedisQueueRepository;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import kr.hhplus.be.server.shared.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Redis 기반 대기열 서비스
 *
 * DB 기반 QueueService를 Redis로 전환한 구현체.
 * Redis Sorted Set(대기열) + Set(활성 토큰) 구조:
 *
 * - WAITING 대기열: Sorted Set (score = timestamp) → O(log N) 삽입/순위 조회
 * - ACTIVE 토큰: Set + 개별 키 TTL → O(1) 존재 확인
 * - 스케줄러: 대기 → 활성 전환 (30초마다)
 */
@Service("redisQueueService")
public class RedisQueueService {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueService.class);

    private static final int MAX_ACTIVE_TOKENS = 100;
    private static final long TOKEN_TTL_SECONDS = 600; // 10분

    private final RedisQueueRepository redisQueueRepository;

    public RedisQueueService(RedisQueueRepository redisQueueRepository) {
        this.redisQueueRepository = redisQueueRepository;
    }

    /**
     * 토큰 발급
     * 1. 기존 토큰 확인 → 있으면 재사용
     * 2. 활성 슬롯 여유 → 즉시 활성화
     * 3. 활성 슬롯 부족 → 대기열 추가
     */
    public QueueTokenResponse issueToken(QueueTokenRequest request) {
        String userId = request.getUserId();

        // 1. 기존 토큰 확인
        var existingToken = redisQueueRepository.findTokenByUserId(userId);
        if (existingToken.isPresent()) {
            String token = existingToken.get();
            if (redisQueueRepository.isActive(token)) {
                return new QueueTokenResponse(token, 0, (int) TOKEN_TTL_SECONDS);
            }
            if (redisQueueRepository.isWaiting(token)) {
                Long position = redisQueueRepository.getWaitingPosition(token);
                int waitSeconds = position != null ? (int) (position * 2 * 60) : 0;
                return new QueueTokenResponse(token, position != null ? position.intValue() : 0, waitSeconds);
            }
        }

        // 2. 새 토큰 생성
        String token = UUID.randomUUID().toString();

        // 3. 활성 슬롯 확인
        long activeCount = redisQueueRepository.countActiveTokens();
        if (activeCount < MAX_ACTIVE_TOKENS) {
            // 즉시 활성화
            redisQueueRepository.saveUserTokenMapping(userId, token);
            redisQueueRepository.activateToken(token, TOKEN_TTL_SECONDS);
            // 토큰→유저 매핑도 저장 (activateToken 내에서 TTL만 설정)
            return new QueueTokenResponse(token, 0, (int) TOKEN_TTL_SECONDS);
        }

        // 4. 대기열 추가
        long position = redisQueueRepository.addToWaitingQueue(token, userId);
        int waitSeconds = (int) (position * 2 * 60); // 1명당 2분 예상
        return new QueueTokenResponse(token, (int) position, waitSeconds);
    }

    /**
     * 토큰 상태 조회
     */
    public QueueTokenResponse getTokenStatus(String tokenValue) {
        // 활성 확인
        if (redisQueueRepository.isActive(tokenValue)) {
            return new QueueTokenResponse(tokenValue, 0, (int) TOKEN_TTL_SECONDS);
        }

        // 대기 확인
        Long position = redisQueueRepository.getWaitingPosition(tokenValue);
        if (position != null) {
            int waitSeconds = (int) (position * 2 * 60);
            return new QueueTokenResponse(tokenValue, position.intValue(), waitSeconds);
        }

        throw new BusinessException("유효하지 않은 토큰입니다.", "invalid-token", 401);
    }

    /**
     * 토큰 검증 (활성 상태인지 확인)
     */
    public void validateToken(String tokenValue) {
        if (!redisQueueRepository.isActive(tokenValue)) {
            // 대기 중인지 확인
            if (redisQueueRepository.isWaiting(tokenValue)) {
                throw new BusinessException("활성화되지 않은 토큰입니다.", "inactive-token", 403);
            }
            throw new BusinessException("유효하지 않은 토큰입니다.", "invalid-token", 401);
        }
    }

    /**
     * 토큰 만료 처리
     */
    public void expireToken(String tokenValue) {
        redisQueueRepository.expireToken(tokenValue);
    }

    /**
     * 대기 → 활성 전환 (스케줄러에서 호출)
     * 활성 슬롯 여유분만큼 대기열 상위 토큰을 활성화
     */
    public int activateWaitingTokens() {
        long activeCount = redisQueueRepository.countActiveTokens();
        if (activeCount >= MAX_ACTIVE_TOKENS) {
            return 0;
        }

        int toActivate = (int) (MAX_ACTIVE_TOKENS - activeCount);
        List<String> waitingTokens = redisQueueRepository.getTopWaitingTokens(toActivate);

        int activated = 0;
        for (String token : waitingTokens) {
            redisQueueRepository.activateToken(token, TOKEN_TTL_SECONDS);
            activated++;
        }

        if (activated > 0) {
            log.info("🎫 Redis 대기 토큰 {}건 활성화 완료 (활성: {}→{})",
                    activated, activeCount, activeCount + activated);
        }

        return activated;
    }
}
