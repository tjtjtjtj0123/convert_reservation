package kr.hhplus.be.server.payment.domain.event;

/**
 * 결제 성공 이벤트
 *
 * 결제가 완료된 후 발행되며, 데이터 플랫폼 전송 등
 * 부가 로직을 트리거합니다.
 */
public class PaymentSuccessEvent {

    private final Long paymentId;
    private final Long reservationId;
    private final String userId;
    private final String concertDate;
    private final Integer seatNumber;
    private final Long amount;

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
