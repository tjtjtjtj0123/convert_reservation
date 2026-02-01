package kr.hhplus.be.server.domain.concert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * 좌석 리포지토리
 */
@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    
    List<Seat> findByConcertDateOrderBySeatNumber(String concertDate);
    
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM Seat s WHERE s.concertDate = :date AND s.seatNumber = :seatNumber")
    Optional<Seat> findByConcertDateAndSeatNumberWithLock(
        @Param("date") String date, 
        @Param("seatNumber") Integer seatNumber
    );
    
    Optional<Seat> findByConcertDateAndSeatNumber(String date, Integer seatNumber);
    
    /**
     * 만료된 좌석들을 한 번에 해제 (Bulk Update)
     * - 좌석 ID 목록으로 한 번에 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Seat s SET s.status = 'AVAILABLE', s.reservedUserId = NULL, s.reservedUntil = NULL " +
           "WHERE s.id IN :seatIds")
    int bulkRelease(@Param("seatIds") List<Long> seatIds);
}
