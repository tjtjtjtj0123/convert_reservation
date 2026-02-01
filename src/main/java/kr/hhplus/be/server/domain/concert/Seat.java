package kr.hhplus.be.server.domain.concert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 좌석 엔티티
 * 
 * 인덱스 설계:
 * - uk_seat_date_number: 날짜+좌석번호 유니크 보장
 * - idx_seat_concert_date: 날짜별 좌석 조회 최적화
 * - idx_seat_status: 상태별 조회 최적화
 */
@Entity
@Table(name = "seat", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_seat_date_number",
           columnNames = {"concert_date", "seat_number"}
       ),
       indexes = {
           @Index(name = "idx_seat_concert_date", columnList = "concert_date"),
           @Index(name = "idx_seat_status", columnList = "status"),
           @Index(name = "idx_seat_reserved_until", columnList = "reserved_until")
       })
public class Seat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "concert_date", nullable = false)
    private String concertDate;
    
    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;
    
    @Column(name = "reserved_user_id")
    private String reservedUserId;
    
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    @Version
    private Long version;

    protected Seat() {
    }

    public Seat(String concertDate, Integer seatNumber) {
        this.concertDate = concertDate;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public String getConcertDate() {
        return concertDate;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public String getReservedUserId() {
        return reservedUserId;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    public void reserve(String userId, LocalDateTime reservedUntil) {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("예약 가능한 좌석이 아닙니다.");
        }
        this.status = SeatStatus.TEMP_HELD;
        this.reservedUserId = userId;
        this.reservedUntil = reservedUntil;
    }

    public void confirm() {
        if (this.status != SeatStatus.TEMP_HELD) {
            throw new IllegalStateException("임시 배정된 좌석이 아닙니다.");
        }
        this.status = SeatStatus.RESERVED;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.reservedUserId = null;
        this.reservedUntil = null;
    }

    public boolean isExpired() {
        return this.status == SeatStatus.TEMP_HELD 
            && this.reservedUntil != null 
            && LocalDateTime.now().isAfter(this.reservedUntil);
    }
}
