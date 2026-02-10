package kr.hhplus.be.server.payment.interfaces.api.dto;

/**
 * 결제 요청 DTO (Interface Layer)
 */
public class PaymentRequest {
    private String userId;
    private Integer seatNumber;
    private String date;

    public PaymentRequest() {
    }

    public PaymentRequest(String userId, Integer seatNumber, String date) {
        this.userId = userId;
        this.seatNumber = seatNumber;
        this.date = date;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
