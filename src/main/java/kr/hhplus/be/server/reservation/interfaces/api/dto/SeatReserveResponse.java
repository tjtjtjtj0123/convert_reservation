package kr.hhplus.be.server.reservation.interfaces.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 좌석 예약 응답 DTO (Interface Layer)
 * 도메인 기반 클린 아키텍처
 */
@Schema(description = "좌석 예약 응답")
public class SeatReserveResponse {
    
    @Schema(description = "좌석 번호", example = "42")
    private Integer seatNumber;
    
    @Schema(description = "임시 예약 만료 시간", example = "2025-12-25T14:35:00")
    private LocalDateTime tempHoldExpires;
    
    @Schema(description = "예약 상태")
    private ReservationStatus status;

    public SeatReserveResponse() {
    }

    public SeatReserveResponse(Integer seatNumber, LocalDateTime tempHoldExpires, ReservationStatus status) {
        this.seatNumber = seatNumber;
        this.tempHoldExpires = tempHoldExpires;
        this.status = status;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(Integer seatNumber) {
        this.seatNumber = seatNumber;
    }

    public LocalDateTime getTempHoldExpires() {
        return tempHoldExpires;
    }

    public void setTempHoldExpires(LocalDateTime tempHoldExpires) {
        this.tempHoldExpires = tempHoldExpires;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    /**
     * 예약 상태 (DTO용)
     */
    @Schema(description = "예약 상태")
    public enum ReservationStatus {
        TEMP_HELD,
        RESERVED
    }
}
