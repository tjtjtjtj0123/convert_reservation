package kr.hhplus.be.server.payment.application.event;

import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.shared.infrastructure.kafka.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 이벤트 리스너
 *
 * 기존: @Async + DataPlatformSendService 직접 호출
 * 변경: 트랜잭션 커밋 후 Kafka로 메시지 발행
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT):
 * - 트랜잭션이 성공적으로 커밋된 후에만 Kafka 메시지를 발행합니다.
 * - 결제 트랜잭션 실패 시 메시지가 발행되지 않습니다.
 *
 * Kafka 발행은 동기로 수행합니다:
 * - Kafka 발행 비용이 매우 적어 @Async 불필요
 * - 발행 실패 시 로그를 남기고 별도 재시도 로직 없이 처리
 *   (Kafka 자체 retry 설정으로 대응)
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final KafkaMessageProducer kafkaMessageProducer;

    public PaymentEventListener(KafkaMessageProducer kafkaMessageProducer) {
        this.kafkaMessageProducer = kafkaMessageProducer;
    }

    /**
     * 결제 성공 이벤트 핸들러
     * - 트랜잭션 커밋 후 Kafka에 결제 정보 메시지 발행
     * - 메시지 키: userId (같은 사용자 이벤트를 같은 파티션으로)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("[PaymentEventListener] 결제 성공 이벤트 수신 → Kafka 발행: {}", event);

        kafkaMessageProducer.send(
                KafkaTopicConfig.TOPIC_PAYMENT_SUCCESS,
                event.getUserId(),
                event
        );
    }
}
