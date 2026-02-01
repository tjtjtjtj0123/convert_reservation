package kr.hhplus.be.server.domain.concert;

/**
 * 좌석 상태 열거형
 */
public enum SeatStatus {
    AVAILABLE("예약 가능"),
    TEMP_HELD("임시 배정"),
    RESERVED("예약 완료");

    private final String description;

    SeatStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
