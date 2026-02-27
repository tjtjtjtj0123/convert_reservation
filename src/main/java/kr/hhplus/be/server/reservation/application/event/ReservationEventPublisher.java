package kr.hhplus.be.server.reservation.application.event;

import kr.hhplus.be.server.reservation.domain.event.ReservationCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 예약 이벤트 퍼블리셔
 *
 * ApplicationEventPublisher를 래핑하여 예약 관련 이벤트를 발행합니다.
 */
@Component
public class ReservationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public ReservationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 예약 완료 이벤트 발행
     */
    public void publishReservationCompleted(ReservationCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
