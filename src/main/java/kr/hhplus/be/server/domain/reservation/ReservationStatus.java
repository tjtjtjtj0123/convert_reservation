package kr.hhplus.be.server.domain.reservation;

/**
 * 예약 상태 열거형
 */
public enum ReservationStatus {
    TEMP_HELD("임시 배정"),
    CONFIRMED("예약 확정"),
    CANCELLED("예약 취소"),
    EXPIRED("예약 만료");

    private final String description;

    ReservationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
