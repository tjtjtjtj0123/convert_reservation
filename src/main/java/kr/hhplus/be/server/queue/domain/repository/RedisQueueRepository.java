package kr.hhplus.be.server.queue.domain.repository;

import java.util.Optional;

/**
 * Redis 기반 대기열 관리 리포지토리 인터페이스 (Domain Layer)
 *
 * Redis Sorted Set + Set 기반의 대기열 관리:
 * - WAITING 대기열: Sorted Set (score = 진입 timestamp)
 * - ACTIVE 토큰: Set + 개별 키 TTL
 * - 토큰-유저 매핑: String 키
 */
public interface RedisQueueRepository {

    /**
     * 대기열에 사용자 추가 (WAITING)
     * ZADD queue:waiting {timestamp} {token}
     *
     * @param token  토큰 값 (UUID)
     * @param userId 사용자 ID
     * @return 대기 순서 (1-based)
     */
    long addToWaitingQueue(String token, String userId);

    /**
     * 대기열에서 토큰 활성화 (WAITING → ACTIVE)
     * ZREM queue:waiting {token} + SADD queue:active {token} + SET queue:token:{token} 유저ID (TTL)
     *
     * @param token      토큰 값
     * @param ttlSeconds 활성 토큰 TTL (초)
     */
    void activateToken(String token, long ttlSeconds);

    /**
     * 토큰 만료 처리
     * SREM queue:active {token} + DEL queue:token:{token}
     *
     * @param token 토큰 값
     */
    void expireToken(String token);

    /**
     * 토큰이 활성 상태인지 확인
     * SISMEMBER queue:active {token}
     *
     * @param token 토큰 값
     * @return 활성 여부
     */
    boolean isActive(String token);

    /**
     * 토큰이 대기 중인지 확인
     * ZRANK queue:waiting {token}
     *
     * @param token 토큰 값
     * @return 대기 여부
     */
    boolean isWaiting(String token);

    /**
     * 대기열에서의 순서 조회 (1-based)
     * ZRANK queue:waiting {token}
     *
     * @param token 토큰 값
     * @return 대기 순서, 없으면 null
     */
    Long getWaitingPosition(String token);

    /**
     * 활성 토큰 수 조회
     * SCARD queue:active
     *
     * @return 활성 토큰 수
     */
    long countActiveTokens();

    /**
     * 대기 중인 토큰 수 조회
     * ZCARD queue:waiting
     *
     * @return 대기 중 토큰 수
     */
    long countWaitingTokens();

    /**
     * 대기열에서 상위 N개 토큰 조회 (활성화 대상)
     * ZRANGE queue:waiting 0 N-1
     *
     * @param count 가져올 수
     * @return 토큰 목록
     */
    java.util.List<String> getTopWaitingTokens(int count);

    /**
     * 사용자 ID로 토큰 조회
     *
     * @param userId 사용자 ID
     * @return 토큰 값
     */
    Optional<String> findTokenByUserId(String userId);

    /**
     * 토큰으로 사용자 ID 조회
     *
     * @param token 토큰 값
     * @return 사용자 ID
     */
    Optional<String> findUserIdByToken(String token);

    /**
     * 사용자-토큰 매핑 저장
     */
    void saveUserTokenMapping(String userId, String token);

    /**
     * 사용자-토큰 매핑 제거
     */
    void removeUserTokenMapping(String userId);
}
