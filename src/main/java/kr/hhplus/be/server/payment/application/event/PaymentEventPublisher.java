package kr.hhplus.be.server.payment.application.event;

import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 퍼블리셔
 *
 * ApplicationEventPublisher를 래핑하여 결제 관련 이벤트를 발행합니다.
 * PaymentService에서 직접 ApplicationEventPublisher를 의존하지 않도록
 * 추상화 계층을 제공합니다.
 */
@Component
public class PaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 결제 성공 이벤트 발행
     */
    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
