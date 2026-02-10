package kr.hhplus.be.server.concert.domain.repository;

import kr.hhplus.be.server.concert.domain.model.Seat;

import java.util.List;
import java.util.Optional;

/**
 * 좌석 리포지토리 인터페이스 (Domain Layer)
 * Infrastructure의 구현에 의존하지 않는 순수 인터페이스
 */
public interface SeatRepository {
    
    /**
     * 좌석 저장
     */
    Seat save(Seat seat);
    
    /**
     * 좌석 목록 저장
     */
    List<Seat> saveAll(List<Seat> seats);
    
    /**
     * ID로 좌석 조회
     */
    Optional<Seat> findById(Long id);
    
    /**
     * 콘서트 날짜로 좌석 목록 조회 (좌석번호 순)
     */
    List<Seat> findByConcertDateOrderBySeatNumber(String concertDate);
    
    /**
     * 날짜와 좌석번호로 좌석 조회 (낙관적 락 포함)
     */
    Optional<Seat> findByConcertDateAndSeatNumberWithLock(String date, Integer seatNumber);
    
    /**
     * 날짜와 좌석번호로 좌석 조회 (비관적 락 FOR UPDATE)
     */
    Optional<Seat> findByConcertDateAndSeatNumberForUpdate(String date, Integer seatNumber);
    
    /**
     * 날짜와 좌석번호로 좌석 조회
     */
    Optional<Seat> findByConcertDateAndSeatNumber(String date, Integer seatNumber);
    
    /**
     * 만료된 좌석들을 한 번에 해제 (Bulk Update)
     */
    int bulkRelease(List<Long> seatIds);
}
