package kr.hhplus.be.server.reservation.domain.model;

/**
 * 예약 상태 열거형 (Domain Layer)
 * 도메인 기반 클린 아키텍처
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
