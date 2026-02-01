package kr.hhplus.be.server.domain.payment;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결제 엔티티 (클린 아키텍처)
 */
@Entity
@Table(name = "payment")
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "amount", nullable = false)
    private Long amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    protected Payment() {
    }

    private Payment(Long reservationId, String userId, Long amount) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public static Payment create(Long reservationId, String userId, Long amount) {
        return new Payment(reservationId, userId, amount);
    }

    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getUserId() {
        return userId;
    }

    public Long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }
}
