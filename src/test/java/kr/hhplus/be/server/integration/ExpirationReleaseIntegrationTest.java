package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.model.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.shared.infrastructure.scheduler.ExpirationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만료 후 좌석 해제 통합 테스트
 * 만료 시간이 도래한 임시 예약은 스케줄러에 의해 해제되어,
 * 좌석이 다시 예약 가능(AVAILABLE) 상태로 돌아가는지 검증한다.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("[통합] 만료 시간 도래 후 좌석 재예약 가능 여부 검증")
class ExpirationReleaseIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ExpirationScheduler expirationScheduler;

    @Test
    @DisplayName("임시 예약 만료 시간이 지나면, 스케줄러가 좌석을 AVAILABLE로 되돌린다")
    void expiredReservation_SeatBecomesAvailable() throws Exception {
        // ========== Given: 이미 만료 시간이 지난 임시 예약 + 좌석 생성 ==========
        String concertDate = "2026-07-20";
        int seatNumber = 30;
        String userId = "expired-user-001";

        // 좌석 생성 및 임시 배정 (과거 시간으로 만료 설정)
        Seat seat = new Seat(concertDate, seatNumber);
        LocalDateTime pastExpiry = LocalDateTime.now().minusMinutes(10);
        seat.reserve(userId, pastExpiry);
        seat = seatRepository.save(seat);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.TEMP_HELD);

        // 예약 레코드 생성 (만료 시간을 과거로 설정)
        Reservation reservation = Reservation.create(userId, seat.getId(), concertDate, seatNumber, 150000L);
        // Reflection으로 reservedUntil을 과거로 강제 설정
        setFieldValue(reservation, "reservedUntil", pastExpiry);
        reservation = reservationRepository.save(reservation);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.TEMP_HELD);

        // ========== When: 스케줄러 수동 실행 (만료 처리) ==========
        expirationScheduler.releaseExpiredReservations();

        // ========== Then: 좌석이 다시 AVAILABLE 상태가 되었는지 확인 ==========

        // 좌석 상태 확인 - bulkRelease로 일괄 해제되었으므로 DB에서 다시 조회
        Seat releasedSeat = seatRepository.findByConcertDateAndSeatNumber(concertDate, seatNumber).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(releasedSeat.getReservedUserId()).isNull();
        assertThat(releasedSeat.getReservedUntil()).isNull();

        // 예약 상태 확인 - EXPIRED로 변경되었는지
        Reservation expiredReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(expiredReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("만료되지 않은 임시 예약은 스케줄러가 해제하지 않는다")
    void notExpiredReservation_SeatRemainsHeld() {
        // ========== Given: 아직 만료되지 않은 임시 예약 ==========
        String concertDate = "2026-07-20";
        int seatNumber = 31;
        String userId = "active-user-001";

        Seat seat = new Seat(concertDate, seatNumber);
        LocalDateTime futureExpiry = LocalDateTime.now().plusMinutes(5);
        seat.reserve(userId, futureExpiry);
        seat = seatRepository.save(seat);

        Reservation reservation = Reservation.create(userId, seat.getId(), concertDate, seatNumber, 150000L);
        reservation = reservationRepository.save(reservation);

        // ========== When: 스케줄러 수동 실행 ==========
        expirationScheduler.releaseExpiredReservations();

        // ========== Then: 좌석이 여전히 TEMP_HELD 상태인지 확인 ==========
        Seat stillHeldSeat = seatRepository.findByConcertDateAndSeatNumber(concertDate, seatNumber).orElseThrow();
        assertThat(stillHeldSeat.getStatus()).isEqualTo(SeatStatus.TEMP_HELD);
        assertThat(stillHeldSeat.getReservedUserId()).isEqualTo(userId);

        // 예약도 여전히 TEMP_HELD 상태
        Reservation stillHeldReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(stillHeldReservation.getStatus()).isEqualTo(ReservationStatus.TEMP_HELD);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
