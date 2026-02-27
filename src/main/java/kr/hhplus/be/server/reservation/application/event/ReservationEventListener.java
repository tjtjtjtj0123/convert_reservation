package kr.hhplus.be.server.reservation.application.event;

import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약 이벤트 리스너
 *
 * 예약 완료 이벤트를 수신하여 부가 로직을 처리합니다:
 * 1. 매진 랭킹 업데이트 (Redis Sorted Set)
 * 2. 데이터 플랫폼에 예약 정보 전송 (Mock)
 */
@Component
public class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);

    private final ConcertRankingService concertRankingService;
    private final DataPlatformSendService dataPlatformSendService;

    public ReservationEventListener(ConcertRankingService concertRankingService,
                                     DataPlatformSendService dataPlatformSendService) {
        this.concertRankingService = concertRankingService;
        this.dataPlatformSendService = dataPlatformSendService;
    }

    /**
     * 예약 완료 이벤트 핸들러
     * - 트랜잭션 커밋 후 비동기로 매진 랭킹 업데이트 및 데이터 플랫폼 전송
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        log.info("[ReservationEventListener] 예약 완료 이벤트 수신: {}", event);

        // 1. 매진 랭킹 업데이트
        try {
            concertRankingService.onSeatReserved(event.getConcertDate());
            log.info("[ReservationEventListener] 매진 랭킹 업데이트 완료 - date={}", event.getConcertDate());
        } catch (Exception e) {
            log.error("[ReservationEventListener] 매진 랭킹 업데이트 실패 - date={}, error={}",
                    event.getConcertDate(), e.getMessage(), e);
        }

        // 2. 데이터 플랫폼 전송
        try {
            dataPlatformSendService.sendReservationData(
                    event.getReservationId(),
                    event.getUserId(),
                    event.getConcertDate(),
                    event.getSeatNumber()
            );
            log.info("[ReservationEventListener] 데이터 플랫폼 전송 완료 - reservationId={}", event.getReservationId());
        } catch (Exception e) {
            log.error("[ReservationEventListener] 데이터 플랫폼 전송 실패 - reservationId={}, error={}",
                    event.getReservationId(), e.getMessage(), e);
        }
    }
}
