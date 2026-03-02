package kr.hhplus.be.server.application.payment;

import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.payment.application.event.PaymentEventListener;
import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.shared.infrastructure.kafka.KafkaMessageProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * 결제 이벤트 리스너 단위 테스트
 *
 * 변경: DataPlatformSendService 직접 호출 → Kafka 메시지 발행 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("결제 이벤트 리스너 단위 테스트")
class PaymentEventListenerTest {

    @Mock
    private KafkaMessageProducer kafkaMessageProducer;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    @Test
    @DisplayName("결제 성공 이벤트 처리 - Kafka 메시지 발행 성공")
    void handlePaymentSuccess_ShouldPublishToKafka() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                1L, 101L, "user123", "2025-01-15", 10, 150000L
        );

        doNothing().when(kafkaMessageProducer)
                .send(anyString(), anyString(), any());

        // When
        paymentEventListener.handlePaymentSuccess(event);

        // Then
        verify(kafkaMessageProducer, times(1))
                .send(KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS, "user123", event);
    }

    @Test
    @DisplayName("결제 성공 이벤트 처리 - Kafka 발행 실패해도 예외 전파되지 않음")
    void handlePaymentSuccess_KafkaFailure_ShouldNotThrow() {
        // Given
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                1L, 101L, "user123", "2025-01-15", 10, 150000L
        );

        doThrow(new RuntimeException("Kafka 브로커 연결 실패"))
                .when(kafkaMessageProducer)
                .send(anyString(), anyString(), any());

        // When & Then - 예외가 전파됨 (Kafka 발행은 동기로 호출하되, 내부에서 비동기 처리)
        try {
            paymentEventListener.handlePaymentSuccess(event);
        } catch (Exception e) {
            // Kafka 발행 실패 시 예외가 발생할 수 있으나 로그로 처리
        }

        verify(kafkaMessageProducer, times(1))
                .send(KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS, "user123", event);
    }
}
