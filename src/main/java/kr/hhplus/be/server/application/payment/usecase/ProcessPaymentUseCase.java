package kr.hhplus.be.server.application.payment.usecase;

import kr.hhplus.be.server.interfaces.api.payment.dto.PaymentRequest;
import kr.hhplus.be.server.interfaces.api.payment.dto.PaymentResponse;

/**
 * 결제 유스케이스 인터페이스 (클린 아키텍처)
 */
public interface ProcessPaymentUseCase {
    PaymentResponse execute(PaymentRequest request, String queueToken);
}
