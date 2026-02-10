package kr.hhplus.be.server.application.point;

import kr.hhplus.be.server.point.application.service.PointService;
import kr.hhplus.be.server.shared.common.exception.BusinessException;
import kr.hhplus.be.server.point.domain.model.PointBalance;
import kr.hhplus.be.server.point.domain.repository.PointBalanceRepository;
import kr.hhplus.be.server.point.interfaces.api.dto.PointBalanceResponse;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeRequest;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 서비스 단위 테스트")
class PointServiceTest {

    @Mock
    private PointBalanceRepository pointBalanceRepository;

    @InjectMocks
    private PointService pointService;

    private PointBalance existingBalance;

    @BeforeEach
    void setUp() {
        existingBalance = new PointBalance("user1");
        existingBalance.charge(100000L);
    }

    @Test
    @DisplayName("포인트를 충전할 수 있다")
    void charge_Success() {
        // given
        PointChargeRequest request = new PointChargeRequest("user1", 50000);
        when(pointBalanceRepository.findById("user1")).thenReturn(Optional.of(existingBalance));
        when(pointBalanceRepository.save(any(PointBalance.class))).thenReturn(existingBalance);

        // when
        PointChargeResponse response = pointService.charge(request);

        // then
        assertThat(response.getTotalPoints()).isEqualTo(150000);
        verify(pointBalanceRepository, times(1)).save(any(PointBalance.class));
    }

    @Test
    @DisplayName("신규 사용자도 포인트를 충전할 수 있다")
    void charge_NewUser_Success() {
        // given
        PointChargeRequest request = new PointChargeRequest("newUser", 30000);
        when(pointBalanceRepository.findById("newUser")).thenReturn(Optional.empty());
        when(pointBalanceRepository.save(any(PointBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PointChargeResponse response = pointService.charge(request);

        // then
        assertThat(response.getTotalPoints()).isEqualTo(30000);
    }

    @Test
    @DisplayName("0 이하 금액 충전 시 예외 발생")
    void charge_InvalidAmount_ThrowsException() {
        // given
        PointChargeRequest request = new PointChargeRequest("user1", 0);

        // when & then
        assertThatThrownBy(() -> pointService.charge(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0보다 커야");
    }

    @Test
    @DisplayName("포인트 잔액을 조회할 수 있다")
    void getBalance_Success() {
        // given
        when(pointBalanceRepository.findById("user1")).thenReturn(Optional.of(existingBalance));

        // when
        PointBalanceResponse response = pointService.getBalance("user1");

        // then
        assertThat(response.getBalance()).isEqualTo(100000);
    }

    @Test
    @DisplayName("포인트를 사용할 수 있다")
    void usePoint_Success() {
        // given
        when(pointBalanceRepository.findByUserIdWithLock("user1")).thenReturn(Optional.of(existingBalance));

        // when
        pointService.usePoint("user1", 50000L);

        // then
        assertThat(existingBalance.getBalance()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("잔액 부족 시 포인트 사용 실패")
    void usePoint_InsufficientBalance_ThrowsException() {
        // given
        when(pointBalanceRepository.findByUserIdWithLock("user1")).thenReturn(Optional.of(existingBalance));

        // when & then
        assertThatThrownBy(() -> pointService.usePoint("user1", 200000L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("잔액이 부족");
    }
}
