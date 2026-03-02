package kr.hhplus.be.server.reservation.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.config.kafka.KafkaTopicConfig;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 예약 완료 Kafka Consumer
 *
 * reservation-completed 토픽의 메시지를 소비하여
 * 매진 랭킹 업데이트 및 데이터 플랫폼에 전송합니다.
 *
 * - Consumer Group: concert-reservation-group
 * - 수동 커밋(Manual Acknowledgment)으로 At-Least-Once 보장
 */
@Component
public class ReservationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReservationKafkaConsumer.class);

    private final ConcertRankingService concertRankingService;
    private final DataPlatformSendService dataPlatformSendService;
    private final ObjectMapper objectMapper;

    public ReservationKafkaConsumer(ConcertRankingService concertRankingService,
                                     DataPlatformSendService dataPlatformSendService,
                                     ObjectMapper objectMapper) {
        this.concertRankingService = concertRankingService;
        this.dataPlatformSendService = dataPlatformSendService;
        this.objectMapper = objectMapper;
    }

    /**
     * 예약 완료 메시지 소비
     *
     * @param message        JSON 직렬화된 ReservationCompletedEvent
     * @param acknowledgment 수동 offset 커밋을 위한 Acknowledgment
     */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_RESERVATION_COMPLETED,
            groupId = "concert-reservation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeReservationCompleted(String message, Acknowledgment acknowledgment) {
        log.info("[Kafka Consumer] 예약 완료 메시지 수신: {}", message);

        try {
            ReservationCompletedEvent event = objectMapper.readValue(message, ReservationCompletedEvent.class);

            // 1. 매진 랭킹 업데이트
            concertRankingService.onSeatReserved(event.getConcertDate());
            log.info("[Kafka Consumer] 매진 랭킹 업데이트 완료 - date={}", event.getConcertDate());

            // 2. 데이터 플랫폼 전송
            dataPlatformSendService.sendReservationData(
                    event.getReservationId(),
                    event.getUserId(),
                    event.getConcertDate(),
                    event.getSeatNumber()
            );
            log.info("[Kafka Consumer] 예약 데이터 플랫폼 전송 완료 - reservationId={}", event.getReservationId());

            // 처리 완료 후 offset 커밋
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka Consumer] 예약 완료 메시지 처리 실패 - message={}, error={}",
                    message, e.getMessage(), e);
            // offset을 커밋하지 않아 재소비됨
        }
    }
}
