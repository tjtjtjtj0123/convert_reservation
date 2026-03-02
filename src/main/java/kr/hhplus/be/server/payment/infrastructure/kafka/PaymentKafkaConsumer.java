package kr.hhplus.be.server.payment.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 결제 성공 Kafka Consumer
 *
 * payment-success 토픽의 메시지를 소비하여 데이터 플랫폼에 전송합니다.
 *
 * - Consumer Group: concert-payment-group
 * - 수동 커밋(Manual Acknowledgment)으로 At-Least-Once 보장
 * - 처리 실패 시 offset을 커밋하지 않아 재소비 가능
 */
@Component
public class PaymentKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaConsumer.class);

    private final DataPlatformSendService dataPlatformSendService;
    private final ObjectMapper objectMapper;

    public PaymentKafkaConsumer(DataPlatformSendService dataPlatformSendService,
                                 ObjectMapper objectMapper) {
        this.dataPlatformSendService = dataPlatformSendService;
        this.objectMapper = objectMapper;
    }

    /**
     * 결제 성공 메시지 소비
     *
     * @param message        JSON 직렬화된 PaymentSuccessEvent
     * @param acknowledgment 수동 offset 커밋을 위한 Acknowledgment
     */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS,
            groupId = "concert-payment-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentSuccess(String message, Acknowledgment acknowledgment) {
        log.info("[Kafka Consumer] 결제 성공 메시지 수신: {}", message);

        try {
            PaymentSuccessEvent event = objectMapper.readValue(message, PaymentSuccessEvent.class);

            dataPlatformSendService.sendPaymentData(
                    event.getPaymentId(),
                    event.getUserId(),
                    event.getConcertDate(),
                    event.getSeatNumber(),
                    event.getAmount()
            );

            // 처리 완료 후 offset 커밋
            acknowledgment.acknowledge();
            log.info("[Kafka Consumer] 결제 데이터 플랫폼 전송 완료 - paymentId={}", event.getPaymentId());

        } catch (Exception e) {
            log.error("[Kafka Consumer] 결제 성공 메시지 처리 실패 - message={}, error={}",
                    message, e.getMessage(), e);
            // offset을 커밋하지 않아 재소비됨 (At-Least-Once)
        }
    }
}
