package kr.hhplus.be.server.point.domain.model;

import jakarta.persistence.*;

/**
 * 포인트 잔액 엔티티 (Domain Layer)
 * 도메인 기반 클린 아키텍처
 */
@Entity
@Table(name = "point_balance")
public class PointBalance {
    
    @Id
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "balance", nullable = false)
    private Long balance = 0L;
    
    @Version
    private Long version;

    protected PointBalance() {
    }

    public PointBalance(String userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public Long getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    // Business Methods
    /**
     * 포인트 충전
     */
    public void charge(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
        this.balance += amount;
    }

    /**
     * 포인트 사용
     */
    public void use(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다.");
        }
        if (this.balance < amount) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        this.balance -= amount;
    }

    /**
     * 잔액 충분 여부 확인
     */
    public boolean hasEnoughBalance(Long amount) {
        return this.balance >= amount;
    }
}
