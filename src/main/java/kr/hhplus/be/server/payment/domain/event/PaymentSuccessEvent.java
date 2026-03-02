package kr.hhplus.be.server.payment.domain.event;

/**
 * 결제 성공 이벤트
 *
 * 결제가 완료된 후 발행되며, 데이터 플랫폼 전송 등
 * 부가 로직을 트리거합니다.
 */
public class PaymentSuccessEvent {

    private Long paymentId;
    private Long reservationId;
    private String userId;
    private String concertDate;
    private Integer seatNumber;
    private Long amount;

    // JSON 역직렬화를 위한 기본 생성자
    public PaymentSuccessEvent() {
    }

    public PaymentSuccessEvent(Long paymentId, Long reservationId, String userId,
                                String concertDate, Integer seatNumber, Long amount) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.concertDate = concertDate;
        this.seatNumber = seatNumber;
        this.amount = amount;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getConcertDate() {
        return concertDate;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public Long getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "PaymentSuccessEvent{" +
                "paymentId=" + paymentId +
                ", reservationId=" + reservationId +
                ", userId='" + userId + '\'' +
                ", concertDate='" + concertDate + '\'' +
                ", seatNumber=" + seatNumber +
                ", amount=" + amount +
                '}';
    }
}
