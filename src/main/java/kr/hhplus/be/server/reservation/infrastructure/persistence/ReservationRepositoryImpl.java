package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 예약 리포지토리 구현체 (Infrastructure Layer)
 * 도메인 기반 클린 아키텍처
 */
@Repository
@Transactional(readOnly = true)
public class ReservationRepositoryImpl implements ReservationRepository {
    
    private final ReservationJpaRepository reservationJpaRepository;

    public ReservationRepositoryImpl(ReservationJpaRepository reservationJpaRepository) {
        this.reservationJpaRepository = reservationJpaRepository;
    }

    @Override
    @Transactional
    public Reservation save(Reservation reservation) {
        return reservationJpaRepository.save(reservation);
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return reservationJpaRepository.findById(id);
    }

    @Override
    public Optional<Reservation> findByUserIdAndSeatIdAndStatus(String userId, Long seatId, ReservationStatus status) {
        return reservationJpaRepository.findByUserIdAndSeatIdAndStatus(userId, seatId, status);
    }

    @Override
    public Optional<Reservation> findByUserIdAndConcertDateAndSeatNumberAndStatus(
            String userId, String concertDate, Integer seatNumber, ReservationStatus status) {
        return reservationJpaRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
            userId, concertDate, seatNumber, status
        );
    }

    @Override
    public List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time) {
        return reservationJpaRepository.findByStatusAndReservedUntilBefore(status, time);
    }

    @Override
    public List<Long> findSeatIdsByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time) {
        return reservationJpaRepository.findSeatIdsByStatusAndReservedUntilBefore(status, time);
    }

    @Override
    @Transactional
    public int bulkExpire(LocalDateTime now) {
        return reservationJpaRepository.bulkExpire(now);
    }
}
