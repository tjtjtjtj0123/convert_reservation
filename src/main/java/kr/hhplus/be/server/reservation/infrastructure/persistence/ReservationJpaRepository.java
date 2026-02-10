package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 예약 JPA Repository (Infrastructure Layer)
 * 도메인 기반 클린 아키텍처
 */
interface ReservationJpaRepository extends JpaRepository<Reservation, Long> {
    
    Optional<Reservation> findByUserIdAndSeatIdAndStatus(String userId, Long seatId, ReservationStatus status);
    
    Optional<Reservation> findByUserIdAndConcertDateAndSeatNumberAndStatus(
        String userId, String concertDate, Integer seatNumber, ReservationStatus status
    );
    
    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    
    @Query("SELECT r.seatId FROM Reservation r WHERE r.status = :status AND r.reservedUntil < :time")
    List<Long> findSeatIdsByStatusAndReservedUntilBefore(
        @Param("status") ReservationStatus status, 
        @Param("time") LocalDateTime time
    );
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = 'EXPIRED' " +
           "WHERE r.status = 'TEMP_HELD' AND r.reservedUntil < :now")
    int bulkExpire(@Param("now") LocalDateTime now);
}
