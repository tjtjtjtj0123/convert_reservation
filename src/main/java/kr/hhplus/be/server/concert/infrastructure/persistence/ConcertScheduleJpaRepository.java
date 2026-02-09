package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.model.ConcertSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 공연 일정 JPA Repository (Infrastructure Layer)
 */
interface ConcertScheduleJpaRepository extends JpaRepository<ConcertSchedule, Long> {
    
    Optional<ConcertSchedule> findByConcertDate(LocalDate concertDate);
    
    @Query("SELECT cs FROM ConcertSchedule cs WHERE cs.availableSeats > 0 AND cs.concertDate >= :today ORDER BY cs.concertDate")
    List<ConcertSchedule> findAvailableSchedules(@Param("today") LocalDate today);
}
