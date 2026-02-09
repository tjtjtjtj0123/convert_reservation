package kr.hhplus.be.server.queue.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 대기열 토큰 엔티티 (Domain Layer)
 * 도메인 기반 클린 아키텍처
 * 
 * 인덱스 설계:
 * - idx_status: 상태별 조회 최적화
 * - idx_status_created: 상태 + 생성시간 정렬 조회 (대기열 순서)
 * - idx_status_expires: 만료 토큰 정리 최적화
 */
@Entity
@Table(name = "queue_token", indexes = {
    @Index(name = "idx_queue_status", columnList = "status"),
    @Index(name = "idx_queue_status_created", columnList = "status, created_at"),
    @Index(name = "idx_queue_status_expires", columnList = "status, expires_at"),
    @Index(name = "idx_queue_user_id", columnList = "user_id")
})
public class QueueToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "token", nullable = false, unique = true)
    private String token;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TokenStatus status = TokenStatus.WAITING;
    
    @Column(name = "position")
    private Integer position;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected QueueToken() {
    }

    public QueueToken(String userId, Integer position) {
        this.userId = userId;
        this.token = UUID.randomUUID().toString();
        this.status = TokenStatus.WAITING;
        this.position = position;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public Integer getPosition() {
        return position;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Business Methods
    /**
     * 토큰 활성화
     */
    public void activate(LocalDateTime expiresAt) {
        this.status = TokenStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.position = 0;
    }

    /**
     * 토큰 만료
     */
    public void expire() {
        this.status = TokenStatus.EXPIRED;
    }

    /**
     * 토큰 활성 상태 확인
     */
    public boolean isActive() {
        return this.status == TokenStatus.ACTIVE 
            && (this.expiresAt == null || LocalDateTime.now().isBefore(this.expiresAt));
    }

    /**
     * 토큰 만료 여부 확인
     */
    public boolean isExpired() {
        if (this.status == TokenStatus.EXPIRED) {
            return true;
        }
        if (this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt)) {
            return true;
        }
        return false;
    }

    /**
     * 대기 순서 업데이트
     */
    public void updatePosition(int position) {
        this.position = position;
    }
}
