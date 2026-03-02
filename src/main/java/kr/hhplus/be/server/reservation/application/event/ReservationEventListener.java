package kr.hhplus.be.server.reservation.application.event;

import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.shared.infrastructure.kafka.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약 이벤트 리스너
 *
 * 기존: @Async + 매진 랭킹 업데이트 + DataPlatformSendService 직접 호출
 * 변경: 트랜잭션 커밋 후 Kafka로 메시지 발행
 *
 * Kafka Consumer(ReservationKafkaConsumer)에서:
 * 1. 매진 랭킹 업데이트 (Redis Sorted Set)
 * 2. 데이터 플랫폼에 예약 정보 전송 (Mock)
 */
@Component
public class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);

    private final KafkaMessageProducer kafkaMessageProducer;

    public ReservationEventListener(KafkaMessageProducer kafkaMessageProducer) {
        this.kafkaMessageProducer = kafkaMessageProducer;
    }

    /**
     * 예약 완료 이벤트 핸들러
     * - 트랜잭션 커밋 후 Kafka에 예약 완료 메시지 발행
     * - 메시지 키: concertDate (같은 콘서트 날짜 이벤트를 같은 파티션으로)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        log.info("[ReservationEventListener] 예약 완료 이벤트 수신 → Kafka 발행: {}", event);

        kafkaMessageProducer.send(
                KafkaTopicConfig.TOPIC_RESERVATION_COMPLETED,
                event.getConcertDate(),
                event
        );
    }
}
