package kr.hhplus.be.server.interfaces.api.point.dto;

/**
 * 포인트 충전 응답 DTO
 */
public class PointChargeResponse {
    private String userId;
    private Integer totalPoints;

    public PointChargeResponse() {
    }

    public PointChargeResponse(String userId, Integer totalPoints) {
        this.userId = userId;
        this.totalPoints = totalPoints;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }
}
