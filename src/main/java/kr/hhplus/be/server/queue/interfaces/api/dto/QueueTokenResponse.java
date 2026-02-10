package kr.hhplus.be.server.queue.interfaces.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대기열 토큰 응답 DTO (Interface Layer)
 */
@Schema(description = "대기열 토큰 응답")
public class QueueTokenResponse {
    
    @Schema(description = "대기열 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;
    
    @Schema(description = "현재 대기 순서", example = "42")
    private Integer position;
    
    @Schema(description = "토큰 만료까지 남은 시간 (초)", example = "3600")
    private Integer expiresIn;

    public QueueTokenResponse() {
    }

    public QueueTokenResponse(String token, Integer position, Integer expiresIn) {
        this.token = token;
        this.position = position;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
