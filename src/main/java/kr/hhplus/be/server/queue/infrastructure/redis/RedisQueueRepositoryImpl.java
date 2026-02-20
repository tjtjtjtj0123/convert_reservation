package kr.hhplus.be.server.queue.infrastructure.redis;

import kr.hhplus.be.server.queue.domain.repository.RedisQueueRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 대기열 관리 구현체
 *
 * 구조:
 * - queue:waiting   (Sorted Set) : 대기열 — member=token, score=timestamp
 * - queue:active    (Set)         : 활성 토큰 집합
 * - queue:token:{token} (String)  : 토큰 → userId 매핑 (TTL 적용)
 * - queue:user:{userId} (String)  : userId → token 매핑
 */
@Repository
public class RedisQueueRepositoryImpl implements RedisQueueRepository {

    private static final String WAITING_KEY = "queue:waiting";
    private static final String ACTIVE_KEY = "queue:active";
    private static final String TOKEN_PREFIX = "queue:token:";
    private static final String USER_PREFIX = "queue:user:";

    private final StringRedisTemplate redisTemplate;

    public RedisQueueRepositoryImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long addToWaitingQueue(String token, String userId) {
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(WAITING_KEY, token, score);
        // 토큰-유저 매핑 저장 (대기열 상태에서도 유저 조회 가능)
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, userId);
        saveUserTokenMapping(userId, token);
        // 대기 순서 반환 (0-based → 1-based)
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, token);
        return rank != null ? rank + 1 : 1;
    }

    @Override
    public void activateToken(String token, long ttlSeconds) {
        // 1. 대기열에서 제거
        redisTemplate.opsForZSet().remove(WAITING_KEY, token);
        // 2. 활성 집합에 추가
        redisTemplate.opsForSet().add(ACTIVE_KEY, token);
        // 3. 토큰 키에 TTL 설정 (자동 만료)
        redisTemplate.expire(TOKEN_PREFIX + token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void expireToken(String token) {
        // 1. 활성 집합에서 제거
        redisTemplate.opsForSet().remove(ACTIVE_KEY, token);
        // 2. 토큰-유저 매핑에서 유저ID 조회 후 매핑 제거
        String userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId != null) {
            removeUserTokenMapping(userId);
        }
        // 3. 토큰 키 삭제
        redisTemplate.delete(TOKEN_PREFIX + token);
    }

    @Override
    public boolean isActive(String token) {
        Boolean isMember = redisTemplate.opsForSet().isMember(ACTIVE_KEY, token);
        if (isMember == null || !isMember) {
            return false;
        }
        // 토큰 키가 만료되었으면 활성 집합에서도 제거
        Boolean exists = redisTemplate.hasKey(TOKEN_PREFIX + token);
        if (exists == null || !exists) {
            redisTemplate.opsForSet().remove(ACTIVE_KEY, token);
            return false;
        }
        return true;
    }

    @Override
    public boolean isWaiting(String token) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, token);
        return rank != null;
    }

    @Override
    public Long getWaitingPosition(String token) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, token);
        return rank != null ? rank + 1 : null;
    }

    @Override
    public long countActiveTokens() {
        Long count = redisTemplate.opsForSet().size(ACTIVE_KEY);
        return count != null ? count : 0;
    }

    @Override
    public long countWaitingTokens() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_KEY);
        return count != null ? count : 0;
    }

    @Override
    public List<String> getTopWaitingTokens(int count) {
        Set<String> tokens = redisTemplate.opsForZSet().range(WAITING_KEY, 0, count - 1);
        return tokens != null ? new ArrayList<>(tokens) : Collections.emptyList();
    }

    @Override
    public Optional<String> findTokenByUserId(String userId) {
        String token = redisTemplate.opsForValue().get(USER_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    @Override
    public Optional<String> findUserIdByToken(String token) {
        String userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        return Optional.ofNullable(userId);
    }

    @Override
    public void saveUserTokenMapping(String userId, String token) {
        redisTemplate.opsForValue().set(USER_PREFIX + userId, token);
    }

    @Override
    public void removeUserTokenMapping(String userId) {
        redisTemplate.delete(USER_PREFIX + userId);
    }
}
