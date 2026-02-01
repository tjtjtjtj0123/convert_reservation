package kr.hhplus.be.server.domain.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 예약 리포지토리
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    
    Optional<Reservation> findByUserIdAndSeatIdAndStatus(String userId, Long seatId, ReservationStatus status);
    
    Optional<Reservation> findByUserIdAndConcertDateAndSeatNumberAndStatus(
        String userId, String concertDate, Integer seatNumber, ReservationStatus status
    );
    
    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    
    /**
     * 만료된 예약들의 좌석 ID 목록 조회 (Bulk Update용)
     */
    @Query("SELECT r.seatId FROM Reservation r WHERE r.status = :status AND r.reservedUntil < :time")
    List<Long> findSeatIdsByStatusAndReservedUntilBefore(
        @Param("status") ReservationStatus status, 
        @Param("time") LocalDateTime time
    );
    
    /**
     * 만료된 예약들을 한 번에 만료 처리 (Bulk Update)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = 'EXPIRED' " +
           "WHERE r.status = 'TEMP_HELD' AND r.reservedUntil < :now")
    int bulkExpire(@Param("now") LocalDateTime now);
}
