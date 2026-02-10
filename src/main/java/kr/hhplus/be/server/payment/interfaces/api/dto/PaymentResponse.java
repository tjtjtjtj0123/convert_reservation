package kr.hhplus.be.server.payment.interfaces.api.dto;

/**
 * 결제 응답 DTO (Interface Layer)
 */
public class PaymentResponse {
    private String paymentId;
    private String userId;
    private Integer seatNumber;
    private Integer amount;
    private Integer remainingPoints;
    private PaymentStatus status;

    public PaymentResponse() {
    }

    public PaymentResponse(String paymentId, String userId, Integer seatNumber, Integer amount, Integer remainingPoints, PaymentStatus status) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.seatNumber = seatNumber;
        this.amount = amount;
        this.remainingPoints = remainingPoints;
        this.status = status;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(Integer seatNumber) {
        this.seatNumber = seatNumber;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public Integer getRemainingPoints() {
        return remainingPoints;
    }

    public void setRemainingPoints(Integer remainingPoints) {
        this.remainingPoints = remainingPoints;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public enum PaymentStatus {
        SUCCESS,
        FAILED
    }
}
