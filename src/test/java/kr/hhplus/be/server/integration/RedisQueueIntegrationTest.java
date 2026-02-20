package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import kr.hhplus.be.server.shared.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis 기반 대기열 통합 테스트
 * Redis Testcontainer 기반
 */
@DisplayName("Redis 대기열 통합 테스트")
class RedisQueueIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        // 대기열 관련 Redis 키 정리
        Set<String> queueKeys = redisTemplate.keys("queue:*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
    }

    @Test
    @DisplayName("토큰 발급 시 활성 슬롯이 있으면 즉시 활성화된다")
    void issueToken_immediateActivation() {
        // When
        QueueTokenResponse response = queueService.issueToken(new QueueTokenRequest("user1"));

        // Then
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getPosition()).isEqualTo(0);

        // 토큰 검증 통과
        queueService.validateToken(response.getToken());
    }

    @Test
    @DisplayName("동일 사용자의 기존 활성 토큰을 재사용한다")
    void issueToken_reuseExistingActiveToken() {
        // Given
        QueueTokenResponse first = queueService.issueToken(new QueueTokenRequest("user1"));

        // When
        QueueTokenResponse second = queueService.issueToken(new QueueTokenRequest("user1"));

        // Then
        assertThat(second.getToken()).isEqualTo(first.getToken());
    }

    @Test
    @DisplayName("활성 토큰이 100개 이상이면 대기열에 추가된다")
    void issueToken_queueWhenFull() {
        // Given - 100개 토큰 활성화
        for (int i = 0; i < 100; i++) {
            queueService.issueToken(new QueueTokenRequest("user" + i));
        }

        // When - 101번째 사용자
        QueueTokenResponse response = queueService.issueToken(new QueueTokenRequest("user-waiting"));

        // Then
        assertThat(response.getPosition()).isGreaterThan(0);
    }

    @Test
    @DisplayName("활성화된 토큰은 검증에 통과한다")
    void validateToken_activeToken() {
        // Given
        QueueTokenResponse response = queueService.issueToken(new QueueTokenRequest("user1"));

        // When & Then (예외 없이 통과)
        queueService.validateToken(response.getToken());
    }

    @Test
    @DisplayName("대기 중인 토큰은 검증에 실패한다")
    void validateToken_waitingToken() {
        // Given - 100개 활성화 후 대기열 추가
        for (int i = 0; i < 100; i++) {
            queueService.issueToken(new QueueTokenRequest("user" + i));
        }
        QueueTokenResponse waitingResponse = queueService.issueToken(new QueueTokenRequest("user-waiting"));

        // When & Then
        assertThatThrownBy(() -> queueService.validateToken(waitingResponse.getToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("활성화되지 않은 토큰");
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 검증에 실패한다")
    void validateToken_invalidToken() {
        // When & Then
        assertThatThrownBy(() -> queueService.validateToken("nonexistent-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");
    }

    @Test
    @DisplayName("토큰 만료 후 검증에 실패한다")
    void expireToken_thenValidateFails() {
        // Given
        QueueTokenResponse response = queueService.issueToken(new QueueTokenRequest("user1"));
        queueService.validateToken(response.getToken()); // 만료 전 통과 확인

        // When
        queueService.expireToken(response.getToken());

        // Then
        assertThatThrownBy(() -> queueService.validateToken(response.getToken()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("토큰 상태 조회 - 활성 상태")
    void getTokenStatus_active() {
        // Given
        QueueTokenResponse issued = queueService.issueToken(new QueueTokenRequest("user1"));

        // When
        QueueTokenResponse status = queueService.getTokenStatus(issued.getToken());

        // Then
        assertThat(status.getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("대기 토큰을 활성화할 수 있다")
    void activateWaitingTokens_movesToActive() {
        // Given - 100개 활성화 후 대기열 추가
        for (int i = 0; i < 100; i++) {
            queueService.issueToken(new QueueTokenRequest("user" + i));
        }
        QueueTokenResponse waitingResponse = queueService.issueToken(new QueueTokenRequest("user-waiting"));

        // 1개 토큰 만료 → 슬롯 확보
        queueService.expireToken(
                queueService.issueToken(new QueueTokenRequest("user0")).getToken()
        );

        // When - 대기 → 활성 전환
        int activated = queueService.activateWaitingTokens();

        // Then
        assertThat(activated).isGreaterThanOrEqualTo(1);
        queueService.validateToken(waitingResponse.getToken()); // 이제 활성화됨
    }
}
