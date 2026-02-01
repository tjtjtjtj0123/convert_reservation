package kr.hhplus.be.server.interfaces.api.concert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 좌석 상태 DTO
 */
@Schema(description = "좌석 상태 정보")
public class SeatStatus {
    
    @Schema(description = "좌석 번호", example = "A-15")
    private Integer seatNumber;
    
    @Schema(description = "좌석 상태", example = "AVAILABLE")
    private SeatStatusEnum status;
    
    @Schema(description = "예매자 UUID (예매된 경우만)", example = "user-123", nullable = true)
    private String reservedBy;
    
    @Schema(description = "임시 홀드 만료 시각 (임시예약인 경우)", example = "2025-12-03T10:35:00", nullable = true)
    private LocalDateTime tempHoldExpires;

    public SeatStatus() {
    }

    public SeatStatus(Integer seatNumber, SeatStatusEnum status, String reservedBy, LocalDateTime tempHoldExpires) {
        this.seatNumber = seatNumber;
        this.status = status;
        this.reservedBy = reservedBy;
        this.tempHoldExpires = tempHoldExpires;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(Integer seatNumber) {
        this.seatNumber = seatNumber;
    }

    public SeatStatusEnum getStatus() {
        return status;
    }

    public void setStatus(SeatStatusEnum status) {
        this.status = status;
    }

    public String getReservedBy() {
        return reservedBy;
    }

    public void setReservedBy(String reservedBy) {
        this.reservedBy = reservedBy;
    }

    public LocalDateTime getTempHoldExpires() {
        return tempHoldExpires;
    }

    public void setTempHoldExpires(LocalDateTime tempHoldExpires) {
        this.tempHoldExpires = tempHoldExpires;
    }

    @Schema(description = "좌석 상태 열거형")
    public enum SeatStatusEnum {
        @Schema(description = "예약 가능")
        AVAILABLE,
        @Schema(description = "예약 완료")
        RESERVED,
        @Schema(description = "임시 예약")
        TEMP_HELD
    }
}
