package kr.hhplus.be.server.concert.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.concert.domain.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 좌석 JPA Repository (Infrastructure Layer)
 * Spring Data JPA를 사용한 데이터 접근 계층
 */
interface SeatJpaRepository extends JpaRepository<Seat, Long> {
    
    List<Seat> findByConcertDateOrderBySeatNumber(String concertDate);
    
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM Seat s WHERE s.concertDate = :date AND s.seatNumber = :seatNumber")
    Optional<Seat> findByConcertDateAndSeatNumberWithLock(
        @Param("date") String date, 
        @Param("seatNumber") Integer seatNumber
    );
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.concertDate = :date AND s.seatNumber = :seatNumber")
    Optional<Seat> findByConcertDateAndSeatNumberForUpdate(
        @Param("date") String date, 
        @Param("seatNumber") Integer seatNumber
    );
    
    Optional<Seat> findByConcertDateAndSeatNumber(String date, Integer seatNumber);
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Seat s SET s.status = 'AVAILABLE', s.reservedUserId = NULL, s.reservedUntil = NULL " +
           "WHERE s.id IN :seatIds")
    int bulkRelease(@Param("seatIds") List<Long> seatIds);
}
