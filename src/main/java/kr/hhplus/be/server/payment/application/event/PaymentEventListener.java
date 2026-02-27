package kr.hhplus.be.server.payment.application.event;

import kr.hhplus.be.server.payment.domain.event.PaymentSuccessEvent;
import kr.hhplus.be.server.payment.infrastructure.external.DataPlatformSendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 이벤트 리스너
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT):
 * - 트랜잭션이 성공적으로 커밋된 후에만 이벤트를 처리합니다.
 * - 결제 트랜잭션 실패 시 데이터 플랫폼 전송이 실행되지 않습니다.
 *
 * @Async:
 * - 이벤트 처리를 비동기로 수행하여 결제 응답 지연을 방지합니다.
 * - 데이터 플랫폼 전송 실패가 결제 트랜잭션에 영향을 주지 않습니다.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final DataPlatformSendService dataPlatformSendService;

    public PaymentEventListener(DataPlatformSendService dataPlatformSendService) {
        this.dataPlatformSendService = dataPlatformSendService;
    }

    /**
     * 결제 성공 이벤트 핸들러
     * - 트랜잭션 커밋 후 비동기로 데이터 플랫폼에 결제 정보 전송
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("[PaymentEventListener] 결제 성공 이벤트 수신: {}", event);

        try {
            dataPlatformSendService.sendPaymentData(
                    event.getPaymentId(),
                    event.getUserId(),
                    event.getConcertDate(),
                    event.getSeatNumber(),
                    event.getAmount()
            );
            log.info("[PaymentEventListener] 데이터 플랫폼 전송 성공 - paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            // 데이터 플랫폼 전송 실패는 결제 트랜잭션에 영향을 주지 않음
            log.error("[PaymentEventListener] 데이터 플랫폼 전송 실패 - paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);
        }
    }
}
