package kr.hhplus.be.server.interfaces.api.concert.dto;

import java.util.List;

/**
 * 좌석 목록 응답 DTO
 */
public class SeatListResponse {
    private String date;
    private List<SeatStatus> seats;

    public SeatListResponse() {
    }

    public SeatListResponse(String date, List<SeatStatus> seats) {
        this.date = date;
        this.seats = seats;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<SeatStatus> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatStatus> seats) {
        this.seats = seats;
    }
}
