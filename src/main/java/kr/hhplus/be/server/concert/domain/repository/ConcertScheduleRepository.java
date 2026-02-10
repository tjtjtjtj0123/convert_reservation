package kr.hhplus.be.server.concert.domain.repository;

import kr.hhplus.be.server.concert.domain.model.ConcertSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 공연 일정 리포지토리 인터페이스 (Domain Layer)
 * Infrastructure의 구현에 의존하지 않는 순수 인터페이스
 */
public interface ConcertScheduleRepository {
    
    /**
     * 공연 일정 저장
     */
    ConcertSchedule save(ConcertSchedule concertSchedule);
    
    /**
     * ID로 공연 일정 조회
     */
    Optional<ConcertSchedule> findById(Long id);
    
    /**
     * 공연 날짜로 일정 조회
     */
    Optional<ConcertSchedule> findByConcertDate(LocalDate concertDate);
    
    /**
     * 예약 가능한 공연 일정 목록 조회
     */
    List<ConcertSchedule> findAvailableSchedules(LocalDate today);
}
