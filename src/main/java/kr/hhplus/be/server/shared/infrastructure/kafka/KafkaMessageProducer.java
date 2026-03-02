package kr.hhplus.be.server.shared.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka 메시지 발행 서비스
 *
 * 도메인 이벤트를 Kafka 토픽으로 발행합니다.
 * - 메시지 키: 순서 보장을 위해 사용 (같은 키 → 같은 파티션)
 * - 메시지 값: JSON 직렬화된 이벤트 데이터
 * - 비동기 발행: KafkaTemplate의 send()는 비동기로 동작
 */
@Component
public class KafkaMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaMessageProducer(KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka 토픽에 메시지 발행
     *
     * @param topic   발행할 토픽 이름
     * @param key     메시지 키 (파티션 결정에 사용)
     * @param payload 발행할 이벤트 객체 (JSON 직렬화)
     */
    public void send(String topic, String key, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, key, message);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka Producer] 메시지 발행 실패 - topic={}, key={}, error={}",
                            topic, key, ex.getMessage(), ex);
                } else {
                    log.info("[Kafka Producer] 메시지 발행 성공 - topic={}, key={}, partition={}, offset={}",
                            topic, key,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("[Kafka Producer] 메시지 직렬화 실패 - topic={}, key={}, error={}",
                    topic, key, e.getMessage(), e);
        }
    }
}
