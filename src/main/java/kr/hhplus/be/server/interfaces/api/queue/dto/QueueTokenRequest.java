package kr.hhplus.be.server.interfaces.api.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대기열 토큰 발급 요청 DTO
 */
@Schema(description = "대기열 토큰 발급 요청")
public class QueueTokenRequest {
    
    @Schema(description = "사용자 ID", example = "user-123", required = true)
    private String userId;

    public QueueTokenRequest() {
    }

    public QueueTokenRequest(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
