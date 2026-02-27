package kr.hhplus.be.server.application.payment;

import kr.hhplus.be.server.payment.application.event.PaymentEventListener;
import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * 결제 이벤트 리스너 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("결제 이벤트 리스너 단위 테스트")
class PaymentEventListenerTest {

    @Mock
    private DataPlatformSendService dataPlatformSendService;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    @Test
    @DisplayName("결제 성공 이벤트 처리 - 데이터 플랫폼 전송 성공")
    void handlePaymentSuccess_ShouldSendToDataPlatform() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                1L, 101L, "user123", "2025-01-15", 10, 150000L
        );

        doNothing().when(dataPlatformSendService)
                .sendPaymentData(1L, "user123", "2025-01-15", 10, 150000L);

        // When
        paymentEventListener.handlePaymentSuccess(event);

        // Then
        verify(dataPlatformSendService, times(1))
                .sendPaymentData(1L, "user123", "2025-01-15", 10, 150000L);
    }

    @Test
    @DisplayName("결제 성공 이벤트 처리 - 데이터 플랫폼 전송 실패해도 예외 발생하지 않음")
    void handlePaymentSuccess_DataPlatformFailure_ShouldNotThrow() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                1L, 101L, "user123", "2025-01-15", 10, 150000L
        );

        doThrow(new RuntimeException("데이터 플랫폼 연결 실패"))
                .when(dataPlatformSendService)
                .sendPaymentData(anyLong(), anyString(), anyString(), anyInt(), anyLong());

        // When - 예외가 발생하지 않아야 함
        paymentEventListener.handlePaymentSuccess(event);

        // Then
        verify(dataPlatformSendService, times(1))
                .sendPaymentData(1L, "user123", "2025-01-15", 10, 150000L);
    }
}
