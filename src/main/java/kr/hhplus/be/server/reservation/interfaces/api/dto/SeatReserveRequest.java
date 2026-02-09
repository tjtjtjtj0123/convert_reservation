package kr.hhplus.be.server.reservation.interfaces.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 좌석 예약 요청 DTO (Interface Layer)
 * 도메인 기반 클린 아키텍처
 */
@Schema(description = "좌석 예약 요청")
public class SeatReserveRequest {
    
    @Schema(description = "사용자 ID", example = "user-123")
    private String userId;
    
    @Schema(description = "공연 날짜", example = "2025-12-25")
    private String date;
    
    @Schema(description = "좌석 번호", example = "42")
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
