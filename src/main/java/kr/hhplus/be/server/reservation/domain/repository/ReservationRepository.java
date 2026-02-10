package kr.hhplus.be.server.reservation.domain.repository;

import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 예약 리포지토리 인터페이스 (Domain Layer)
 * 도메인 기반 클린 아키텍처
 * Infrastructure의 구현에 의존하지 않는 순수 인터페이스
 */
public interface ReservationRepository {
    
    /**
     * 예약 저장
     */
    Reservation save(Reservation reservation);
    
    /**
     * ID로 예약 조회
     */
    Optional<Reservation> findById(Long id);
    
    /**
     * 사용자 ID, 좌석 ID, 상태로 예약 조회
     */
    Optional<Reservation> findByUserIdAndSeatIdAndStatus(String userId, Long seatId, ReservationStatus status);
    
    /**
     * 사용자 ID, 공연 날짜, 좌석 번호, 상태로 예약 조회
     */
    Optional<Reservation> findByUserIdAndConcertDateAndSeatNumberAndStatus(
        String userId, String concertDate, Integer seatNumber, ReservationStatus status
    );
    
    /**
     * 상태와 만료 시간으로 예약 조회
     */
    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    
    /**
     * 만료된 예약들의 좌석 ID 목록 조회 (Bulk Update용)
     */
    List<Long> findSeatIdsByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    
    /**
     * 만료된 예약들을 한 번에 만료 처리 (Bulk Update)
     */
    int bulkExpire(LocalDateTime now);
}
