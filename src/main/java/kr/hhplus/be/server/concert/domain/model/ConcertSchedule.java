package kr.hhplus.be.server.concert.domain.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * 공연 일정 엔티티
 */
@Entity
@Table(name = "concert_schedule")
public class ConcertSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "concert_date", nullable = false, unique = true)
    private LocalDate concertDate;
    
    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats = 50;
    
    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats = 50;

    protected ConcertSchedule() {
    }

    public ConcertSchedule(LocalDate concertDate) {
        this.concertDate = concertDate;
        this.totalSeats = 50;
        this.availableSeats = 50;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getConcertDate() {
        return concertDate;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public Integer getAvailableSeats() {
        return availableSeats;
    }

    public void decreaseAvailableSeats() {
        if (this.availableSeats <= 0) {
            throw new IllegalStateException("예약 가능한 좌석이 없습니다.");
        }
        this.availableSeats--;
    }

    public void increaseAvailableSeats() {
        if (this.availableSeats >= this.totalSeats) {
            throw new IllegalStateException("좌석 수가 최대치를 초과할 수 없습니다.");
        }
        this.availableSeats++;
    }
}
