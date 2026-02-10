package kr.hhplus.be.server.queue.domain.model;

/**
 * 토큰 상태 열거형 (Domain Layer)
 */
public enum TokenStatus {
    WAITING("대기 중"),
    ACTIVE("활성"),
    EXPIRED("만료");

    private final String description;

    TokenStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
