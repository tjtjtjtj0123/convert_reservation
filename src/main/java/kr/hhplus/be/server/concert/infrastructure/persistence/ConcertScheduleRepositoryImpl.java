package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.model.ConcertSchedule;
import kr.hhplus.be.server.concert.domain.repository.ConcertScheduleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 공연 일정 리포지토리 구현체 (Infrastructure Layer)
 */
@Repository
@Transactional(readOnly = true)
public class ConcertScheduleRepositoryImpl implements ConcertScheduleRepository {
    
    private final ConcertScheduleJpaRepository concertScheduleJpaRepository;

    public ConcertScheduleRepositoryImpl(ConcertScheduleJpaRepository concertScheduleJpaRepository) {
        this.concertScheduleJpaRepository = concertScheduleJpaRepository;
    }

    @Override
    @Transactional
    public ConcertSchedule save(ConcertSchedule concertSchedule) {
        return concertScheduleJpaRepository.save(concertSchedule);
    }

    @Override
    public Optional<ConcertSchedule> findById(Long id) {
        return concertScheduleJpaRepository.findById(id);
    }

    @Override
    public Optional<ConcertSchedule> findByConcertDate(LocalDate concertDate) {
        return concertScheduleJpaRepository.findByConcertDate(concertDate);
    }

    @Override
    public List<ConcertSchedule> findAvailableSchedules(LocalDate today) {
        return concertScheduleJpaRepository.findAvailableSchedules(today);
    }
}
