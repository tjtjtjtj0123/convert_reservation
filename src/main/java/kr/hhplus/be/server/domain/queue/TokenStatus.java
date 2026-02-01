package kr.hhplus.be.server.domain.queue;

/**
 * 토큰 상태 열거형
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
