package kr.hhplus.be.server.reservation.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 예약 엔티티 (Domain Layer)
 * 도메인 기반 클린 아키텍처
 * 
 * 인덱스 설계:
 * - idx_reservation_status_until: 만료 예약 조회 최적화
 * - idx_reservation_user_date_seat: 사용자별 예약 조회 최적화
 */
@Entity
@Table(name = "reservation", indexes = {
    @Index(name = "idx_reservation_status", columnList = "status"),
    @Index(name = "idx_reservation_status_until", columnList = "status, reserved_until"),
    @Index(name = "idx_reservation_user_id", columnList = "user_id"),
    @Index(name = "idx_reservation_seat_id", columnList = "seat_id"),
    @Index(name = "idx_reservation_user_date_seat", columnList = "user_id, concert_date, seat_number, status")
})
public class Reservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "seat_id", nullable = false)
    private Long seatId;
    
    @Column(name = "concert_date", nullable = false)
    private String concertDate;
    
    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;
    
    @Column(name = "price", nullable = false)
    private Long price;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;
    
    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;
    
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;
    
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected Reservation() {
    }

    private Reservation(String userId, Long seatId, String concertDate, Integer seatNumber, Long price) {
        this.userId = userId;
        this.seatId = seatId;
        this.concertDate = concertDate;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = ReservationStatus.TEMP_HELD;
        this.reservedAt = LocalDateTime.now();
        this.reservedUntil = LocalDateTime.now().plusMinutes(5);
    }

    /**
     * 예약 생성 팩토리 메서드 (도메인 로직)
     */
    public static Reservation create(String userId, Long seatId, String concertDate, Integer seatNumber, Long price) {
        return new Reservation(userId, seatId, concertDate, seatNumber, price);
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public String getConcertDate() {
        return concertDate;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public Long getPrice() {
        return price;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    /**
     * 예약 확정 (도메인 비즈니스 로직)
     */
    public void confirm() {
        if (this.status != ReservationStatus.TEMP_HELD) {
            throw new IllegalStateException("임시 예약 상태가 아닙니다.");
        }
        if (isExpired()) {
            throw new IllegalStateException("예약 시간이 만료되었습니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * 예약 취소 (도메인 비즈니스 로직)
     */
    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * 예약 만료 (도메인 비즈니스 로직)
     */
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }

    /**
     * 예약 만료 여부 확인 (도메인 비즈니스 로직)
     */
    public boolean isExpired() {
        return this.reservedUntil != null && LocalDateTime.now().isAfter(this.reservedUntil);
    }
}
