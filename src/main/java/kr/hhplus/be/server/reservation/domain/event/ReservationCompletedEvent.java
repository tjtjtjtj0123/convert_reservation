package kr.hhplus.be.server.reservation.domain.event;

/**
 * 예약 완료 이벤트
 *
 * 좌석 예약이 완료된 후 발행되며,
 * 매진 랭킹 업데이트 등 부가 로직을 트리거합니다.
 */
public class ReservationCompletedEvent {

    private Long reservationId;
    private String userId;
    private String concertDate;
    private Integer seatNumber;

    // JSON 역직렬화를 위한 기본 생성자
    public ReservationCompletedEvent() {
    }

    public ReservationCompletedEvent(Long reservationId, String userId,
                                      String concertDate, Integer seatNumber) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.concertDate = concertDate;
        this.seatNumber = seatNumber;
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

    @Override
    public String toString() {
        return "ReservationCompletedEvent{" +
                "reservationId=" + reservationId +
                ", userId='" + userId + '\'' +
                ", concertDate='" + concertDate + '\'' +
                ", seatNumber=" + seatNumber +
                '}';
    }
}
