package kr.hhplus.be.server.payment.domain.model;

/**
 * 결제 상태 열거형 (Domain Layer)
 */
public enum PaymentStatus {
    COMPLETED("결제 완료"),
    CANCELLED("결제 취소"),
    FAILED("결제 실패");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
