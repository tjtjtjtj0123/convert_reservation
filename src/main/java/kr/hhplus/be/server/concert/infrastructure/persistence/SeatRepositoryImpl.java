package kr.hhplus.be.server.concert.infrastructure.persistence;

import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 좌석 리포지토리 구현체 (Infrastructure Layer)
 * Domain의 SeatRepository 인터페이스를 구현
 */
@Repository
@Transactional(readOnly = true)
public class SeatRepositoryImpl implements SeatRepository {
    
    private final SeatJpaRepository seatJpaRepository;

    public SeatRepositoryImpl(SeatJpaRepository seatJpaRepository) {
        this.seatJpaRepository = seatJpaRepository;
    }

    @Override
    @Transactional
    public Seat save(Seat seat) {
        return seatJpaRepository.save(seat);
    }

    @Override
    @Transactional
    public List<Seat> saveAll(List<Seat> seats) {
        return seatJpaRepository.saveAll(seats);
    }

    @Override
    public Optional<Seat> findById(Long id) {
        return seatJpaRepository.findById(id);
    }

    @Override
    public List<Seat> findByConcertDateOrderBySeatNumber(String concertDate) {
        return seatJpaRepository.findByConcertDateOrderBySeatNumber(concertDate);
    }

    @Override
    public Optional<Seat> findByConcertDateAndSeatNumberWithLock(String date, Integer seatNumber) {
        return seatJpaRepository.findByConcertDateAndSeatNumberWithLock(date, seatNumber);
    }

    @Override
    public Optional<Seat> findByConcertDateAndSeatNumberForUpdate(String date, Integer seatNumber) {
        return seatJpaRepository.findByConcertDateAndSeatNumberForUpdate(date, seatNumber);
    }

    @Override
    public Optional<Seat> findByConcertDateAndSeatNumber(String date, Integer seatNumber) {
        return seatJpaRepository.findByConcertDateAndSeatNumber(date, seatNumber);
    }

    @Override
    @Transactional
    public int bulkRelease(List<Long> seatIds) {
        return seatJpaRepository.bulkRelease(seatIds);
    }
}
