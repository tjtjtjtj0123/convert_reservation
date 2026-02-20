package kr.hhplus.be.server.application.queue;

import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.queue.domain.repository.RedisQueueRepository;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import kr.hhplus.be.server.shared.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("대기열 서비스 단위 테스트 (Redis 기반)")
class QueueServiceTest {

    @Mock
    private RedisQueueRepository redisQueueRepository;

    @InjectMocks
    private QueueService queueService;

    @Test
    @DisplayName("신규 사용자에게 토큰을 발급할 수 있다 - 즉시 활성화")
    void issueToken_NewUser_ImmediateActivation() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("newUser");
        when(redisQueueRepository.findTokenByUserId("newUser")).thenReturn(Optional.empty());
        when(redisQueueRepository.countActiveTokens()).thenReturn(50L);

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getPosition()).isEqualTo(0); // 즉시 활성화
        verify(redisQueueRepository).saveUserTokenMapping(eq("newUser"), anyString());
        verify(redisQueueRepository).activateToken(anyString(), eq(600L));
    }

    @Test
    @DisplayName("기존 활성 토큰이 있으면 재발급하지 않는다")
    void issueToken_ExistingActiveToken_ReturnsExisting() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("user1");
        when(redisQueueRepository.findTokenByUserId("user1")).thenReturn(Optional.of("existing-token"));
        when(redisQueueRepository.isActive("existing-token")).thenReturn(true);

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getToken()).isEqualTo("existing-token");
        assertThat(response.getPosition()).isEqualTo(0);
        verify(redisQueueRepository, never()).activateToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("기존 대기 토큰이 있으면 대기 위치를 반환한다")
    void issueToken_ExistingWaitingToken_ReturnsPosition() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("user2");
        when(redisQueueRepository.findTokenByUserId("user2")).thenReturn(Optional.of("waiting-token"));
        when(redisQueueRepository.isActive("waiting-token")).thenReturn(false);
        when(redisQueueRepository.isWaiting("waiting-token")).thenReturn(true);
        when(redisQueueRepository.getWaitingPosition("waiting-token")).thenReturn(5L);

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getToken()).isEqualTo("waiting-token");
        assertThat(response.getPosition()).isEqualTo(5);
    }

    @Test
    @DisplayName("활성화된 토큰이 100개 초과 시 대기열에 추가된다")
    void issueToken_QueueFull_WaitingPosition() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("user3");
        when(redisQueueRepository.findTokenByUserId("user3")).thenReturn(Optional.empty());
        when(redisQueueRepository.countActiveTokens()).thenReturn(100L);
        when(redisQueueRepository.addToWaitingQueue(anyString(), eq("user3"))).thenReturn(11L);

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getPosition()).isEqualTo(11);
        verify(redisQueueRepository).addToWaitingQueue(anyString(), eq("user3"));
    }

    @Test
    @DisplayName("활성화된 토큰은 검증에 통과한다")
    void validateToken_ActiveToken_Success() {
        // given
        when(redisQueueRepository.isActive("active-token")).thenReturn(true);

        // when & then (예외 없이 통과)
        queueService.validateToken("active-token");
    }

    @Test
    @DisplayName("대기 중인 토큰은 검증에 실패한다")
    void validateToken_WaitingToken_ThrowsException() {
        // given
        when(redisQueueRepository.isActive("waiting-token")).thenReturn(false);
        when(redisQueueRepository.isWaiting("waiting-token")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> queueService.validateToken("waiting-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("활성화되지 않은 토큰");
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 검증에 실패한다")
    void validateToken_InvalidToken_ThrowsException() {
        // given
        when(redisQueueRepository.isActive("invalid-token")).thenReturn(false);
        when(redisQueueRepository.isWaiting("invalid-token")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> queueService.validateToken("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");
    }

    @Test
    @DisplayName("토큰 상태를 조회할 수 있다 - 활성")
    void getTokenStatus_Active_Success() {
        // given
        when(redisQueueRepository.isActive("active-token")).thenReturn(true);

        // when
        QueueTokenResponse response = queueService.getTokenStatus("active-token");

        // then
        assertThat(response.getToken()).isEqualTo("active-token");
        assertThat(response.getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("토큰 상태를 조회할 수 있다 - 대기")
    void getTokenStatus_Waiting_Success() {
        // given
        when(redisQueueRepository.isActive("waiting-token")).thenReturn(false);
        when(redisQueueRepository.getWaitingPosition("waiting-token")).thenReturn(3L);

        // when
        QueueTokenResponse response = queueService.getTokenStatus("waiting-token");

        // then
        assertThat(response.getPosition()).isEqualTo(3);
    }

    @Test
    @DisplayName("존재하지 않는 토큰 조회 시 예외 발생")
    void getTokenStatus_InvalidToken_ThrowsException() {
        // given
        when(redisQueueRepository.isActive("invalid")).thenReturn(false);
        when(redisQueueRepository.getWaitingPosition("invalid")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> queueService.getTokenStatus("invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");
    }

    @Test
    @DisplayName("토큰을 만료시킬 수 있다")
    void expireToken_Success() {
        // when
        queueService.expireToken("some-token");

        // then
        verify(redisQueueRepository).expireToken("some-token");
    }

    @Test
    @DisplayName("대기 토큰을 활성화할 수 있다")
    void activateWaitingTokens_Success() {
        // given
        when(redisQueueRepository.countActiveTokens()).thenReturn(95L);
        when(redisQueueRepository.getTopWaitingTokens(5)).thenReturn(List.of("t1", "t2", "t3"));

        // when
        int activated = queueService.activateWaitingTokens();

        // then
        assertThat(activated).isEqualTo(3);
        verify(redisQueueRepository, times(3)).activateToken(anyString(), eq(600L));
    }

    @Test
    @DisplayName("활성 토큰이 가득 차면 활성화하지 않는다")
    void activateWaitingTokens_FullCapacity() {
        // given
        when(redisQueueRepository.countActiveTokens()).thenReturn(100L);

        // when
        int activated = queueService.activateWaitingTokens();

        // then
        assertThat(activated).isEqualTo(0);
        verify(redisQueueRepository, never()).getTopWaitingTokens(anyInt());
    }
}
