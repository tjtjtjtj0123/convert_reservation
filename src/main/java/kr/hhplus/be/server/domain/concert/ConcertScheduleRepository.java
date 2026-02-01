package kr.hhplus.be.server.domain.concert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 공연 일정 리포지토리
 */
@Repository
public interface ConcertScheduleRepository extends JpaRepository<ConcertSchedule, Long> {
    
    Optional<ConcertSchedule> findByConcertDate(LocalDate concertDate);
    
    @Query("SELECT cs FROM ConcertSchedule cs WHERE cs.availableSeats > 0 AND cs.concertDate >= :today ORDER BY cs.concertDate")
    List<ConcertSchedule> findAvailableSchedules(@Param("today") LocalDate today);
}
