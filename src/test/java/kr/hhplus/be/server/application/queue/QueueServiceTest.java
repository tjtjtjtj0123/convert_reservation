package kr.hhplus.be.server.application.queue;

import kr.hhplus.be.server.common.exception.BusinessException;
import kr.hhplus.be.server.domain.queue.QueueToken;
import kr.hhplus.be.server.domain.queue.QueueTokenRepository;
import kr.hhplus.be.server.domain.queue.TokenStatus;
import kr.hhplus.be.server.interfaces.api.queue.dto.QueueTokenRequest;
import kr.hhplus.be.server.interfaces.api.queue.dto.QueueTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("대기열 서비스 단위 테스트")
class QueueServiceTest {

    @Mock
    private QueueTokenRepository tokenRepository;

    @InjectMocks
    private QueueService queueService;

    private QueueToken activeToken;
    private QueueToken waitingToken;

    @BeforeEach
    void setUp() {
        activeToken = new QueueToken("user1", 0);
        activeToken.activate(LocalDateTime.now().plusMinutes(10));

        waitingToken = new QueueToken("user2", 5);
    }

    @Test
    @DisplayName("신규 사용자에게 토큰을 발급할 수 있다")
    void issueToken_NewUser_Success() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("newUser");
        when(tokenRepository.findByUserId("newUser")).thenReturn(Optional.empty());
        when(tokenRepository.countActive()).thenReturn(50L);
        when(tokenRepository.countWaiting()).thenReturn(0L);
        when(tokenRepository.save(any(QueueToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getToken()).isNotNull();
        assertThat(response.getPosition()).isEqualTo(0); // 바로 활성화
        verify(tokenRepository, times(1)).save(any(QueueToken.class));
    }

    @Test
    @DisplayName("기존 유효한 토큰이 있으면 재발급하지 않는다")
    void issueToken_ExistingValidToken_ReturnsExisting() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("user1");
        when(tokenRepository.findByUserId("user1")).thenReturn(Optional.of(activeToken));

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getToken()).isEqualTo(activeToken.getToken());
        verify(tokenRepository, never()).save(any(QueueToken.class));
    }

    @Test
    @DisplayName("활성화된 토큰이 100개 초과 시 대기열에 추가된다")
    void issueToken_QueueFull_WaitingPosition() {
        // given
        QueueTokenRequest request = new QueueTokenRequest("user3");
        when(tokenRepository.findByUserId("user3")).thenReturn(Optional.empty());
        when(tokenRepository.countActive()).thenReturn(100L);
        when(tokenRepository.countWaiting()).thenReturn(10L);
        when(tokenRepository.save(any(QueueToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        QueueTokenResponse response = queueService.issueToken(request);

        // then
        assertThat(response.getPosition()).isEqualTo(11); // 대기열 11번째
    }

    @Test
    @DisplayName("토큰 상태를 조회할 수 있다")
    void getTokenStatus_Success() {
        // given
        when(tokenRepository.findByToken(activeToken.getToken())).thenReturn(Optional.of(activeToken));

        // when
        QueueTokenResponse response = queueService.getTokenStatus(activeToken.getToken());

        // then
        assertThat(response.getToken()).isEqualTo(activeToken.getToken());
    }

    @Test
    @DisplayName("존재하지 않는 토큰 조회 시 예외 발생")
    void getTokenStatus_InvalidToken_ThrowsException() {
        // given
        when(tokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> queueService.getTokenStatus("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은 토큰");
    }

    @Test
    @DisplayName("활성화된 토큰은 검증에 통과한다")
    void validateToken_ActiveToken_Success() {
        // given
        when(tokenRepository.findByToken(activeToken.getToken())).thenReturn(Optional.of(activeToken));

        // when & then (예외 없이 통과)
        queueService.validateToken(activeToken.getToken());
    }

    @Test
    @DisplayName("대기 중인 토큰은 검증에 실패한다")
    void validateToken_WaitingToken_ThrowsException() {
        // given
        when(tokenRepository.findByToken(waitingToken.getToken())).thenReturn(Optional.of(waitingToken));

        // when & then
        assertThatThrownBy(() -> queueService.validateToken(waitingToken.getToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("활성화되지 않은 토큰");
    }

    @Test
    @DisplayName("토큰을 만료시킬 수 있다")
    void expireToken_Success() {
        // given
        when(tokenRepository.findByToken(activeToken.getToken())).thenReturn(Optional.of(activeToken));

        // when
        queueService.expireToken(activeToken.getToken());

        // then
        assertThat(activeToken.getStatus()).isEqualTo(TokenStatus.EXPIRED);
    }
}
