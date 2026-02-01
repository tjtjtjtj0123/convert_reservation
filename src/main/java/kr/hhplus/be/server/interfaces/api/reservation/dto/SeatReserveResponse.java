package kr.hhplus.be.server.interfaces.api.reservation.dto;

import java.time.LocalDateTime;

/**
 * 좌석 예약 응답 DTO
 */
public class SeatReserveResponse {
    private Integer seatNumber;
    private LocalDateTime tempHoldExpires;
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

    public enum ReservationStatus {
        TEMP_HELD,
        RESERVED
    }
}
