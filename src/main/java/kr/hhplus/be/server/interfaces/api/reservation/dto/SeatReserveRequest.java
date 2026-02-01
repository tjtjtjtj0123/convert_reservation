package kr.hhplus.be.server.interfaces.api.reservation.dto;

/**
 * 좌석 예약 요청 DTO
 */
public class SeatReserveRequest {
    private String userId;
    private String date;
    private Integer seatNumber;

    public SeatReserveRequest() {
    }

    public SeatReserveRequest(String userId, String date, Integer seatNumber) {
        this.userId = userId;
        this.date = date;
        this.seatNumber = seatNumber;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(Integer seatNumber) {
        this.seatNumber = seatNumber;
    }
}
