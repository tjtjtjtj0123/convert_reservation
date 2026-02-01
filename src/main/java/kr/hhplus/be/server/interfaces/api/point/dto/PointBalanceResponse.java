package kr.hhplus.be.server.interfaces.api.point.dto;

/**
 * 포인트 잔액 응답 DTO
 */
public class PointBalanceResponse {
    private String userId;
    private Integer balance;

    public PointBalanceResponse() {
    }

    public PointBalanceResponse(String userId, Integer balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getBalance() {
        return balance;
    }

    public void setBalance(Integer balance) {
        this.balance = balance;
    }
}
