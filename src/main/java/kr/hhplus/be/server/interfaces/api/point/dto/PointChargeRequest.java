package kr.hhplus.be.server.interfaces.api.point.dto;

/**
 * 포인트 충전 요청 DTO
 */
public class PointChargeRequest {
    private String userId;
    private Integer amount;

    public PointChargeRequest() {
    }

    public PointChargeRequest(String userId, Integer amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }
}
