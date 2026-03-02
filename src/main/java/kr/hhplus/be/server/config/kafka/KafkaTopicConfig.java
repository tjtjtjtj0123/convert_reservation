package kr.hhplus.be.server.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topic 설정
 *
 * 토픽 구성:
 * - payment-success: 결제 성공 이벤트 (파티션 3개, Replication Factor 2)
 * - reservation-completed: 예약 완료 이벤트 (파티션 3개, Replication Factor 2)
 *
 * 파티션 설계:
 * - 3개의 파티션으로 병렬 처리 가능
 * - 메시지 키(userId)로 같은 사용자의 메시지를 같은 파티션에 할당하여 순서 보장
 *
 * Replication Factor: 2
 * - 최소 2개의 브로커에 복제하여 1대 장애 시에도 데이터 유실 방지
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_PAYMENT_SUCCESS = "payment-success";
    public static final String TOPIC_RESERVATION_COMPLETED = "reservation-completed";

    @Bean
    public NewTopic paymentSuccessTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_SUCCESS)
                .partitions(3)
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic reservationCompletedTopic() {
        return TopicBuilder.name(TOPIC_RESERVATION_COMPLETED)
                .partitions(3)
                .replicas(2)
                .build();
    }
}
