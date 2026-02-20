package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.model.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.payment.application.service.PaymentService;
import kr.hhplus.be.server.payment.domain.model.Payment;
import kr.hhplus.be.server.payment.domain.model.PaymentStatus;
import kr.hhplus.be.server.payment.domain.repository.PaymentRepository;
import kr.hhplus.be.server.payment.interfaces.api.dto.PaymentRequest;
import kr.hhplus.be.server.payment.interfaces.api.dto.PaymentResponse;
import kr.hhplus.be.server.point.application.service.PointService;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeRequest;
import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.queue.domain.model.QueueToken;
import kr.hhplus.be.server.queue.domain.model.TokenStatus;
import kr.hhplus.be.server.queue.domain.repository.QueueTokenRepository;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import kr.hhplus.be.server.reservation.application.service.ReservationService;
import kr.hhplus.be.server.reservation.domain.model.Reservation;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveRequest;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E 흐름 통합 테스트
 * 토큰 발급 → 좌석 예약 → 결제 완료까지의 전체 흐름을 검증한다.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("[통합] 토큰 발급 → 좌석 예약 → 결제 완료 E2E 흐름")
class ReservationE2EIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PointService pointService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("유저가 토큰을 발급받고 → 좌석 예약 → 결제 완료까지 성공한다")
    void fullFlow_TokenIssue_Reserve_Pay_Success() {
        // ========== Given: 테스트 데이터 준비 ==========
        String userId = "e2e-user-001";
        String concertDate = "2026-06-15";
        int seatNumber = 25;

        // 좌석 생성
        seatRepository.save(new Seat(concertDate, seatNumber));

        // ========== 1단계: 포인트 충전 ==========
        pointService.charge(new PointChargeRequest(userId, 500000));

        // ========== 2단계: 토큰 발급 ==========
        QueueTokenResponse tokenResponse = queueService.issueToken(new QueueTokenRequest(userId));
        assertThat(tokenResponse).isNotNull();
        assertThat(tokenResponse.getToken()).isNotBlank();

        String token = tokenResponse.getToken();

        // 토큰이 ACTIVE 상태인지 확인 (대기열이 비어있으므로 바로 활성화)
        QueueToken queueToken = queueTokenRepository.findByToken(token).orElseThrow();
        assertThat(queueToken.getStatus()).isEqualTo(TokenStatus.ACTIVE);

        // ========== 3단계: 좌석 예약 ==========
        SeatReserveRequest reserveRequest = new SeatReserveRequest(userId, concertDate, seatNumber);
        SeatReserveResponse reserveResponse = reservationService.reserveSeat(reserveRequest, token);

        assertThat(reserveResponse).isNotNull();
        assertThat(reserveResponse.getSeatNumber()).isEqualTo(seatNumber);
        assertThat(reserveResponse.getStatus()).isEqualTo(SeatReserveResponse.ReservationStatus.TEMP_HELD);
        assertThat(reserveResponse.getTempHoldExpires()).isNotNull();

        // 좌석이 TEMP_HELD 상태인지 DB에서 확인
        Seat reservedSeat = seatRepository.findByConcertDateAndSeatNumber(concertDate, seatNumber).orElseThrow();
        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.TEMP_HELD);
        assertThat(reservedSeat.getReservedUserId()).isEqualTo(userId);

        // 예약 레코드가 TEMP_HELD 상태인지 확인
        Optional<Reservation> reservation = reservationRepository
                .findByUserIdAndConcertDateAndSeatNumberAndStatus(userId, concertDate, seatNumber, ReservationStatus.TEMP_HELD);
        assertThat(reservation).isPresent();

        // ========== 4단계: 결제 ==========
        PaymentRequest paymentRequest = new PaymentRequest(userId, seatNumber, concertDate);
        PaymentResponse paymentResponse = paymentService.processPayment(paymentRequest, token);

        assertThat(paymentResponse).isNotNull();
        assertThat(paymentResponse.getUserId()).isEqualTo(userId);
        assertThat(paymentResponse.getSeatNumber()).isEqualTo(seatNumber);
        assertThat(paymentResponse.getAmount()).isEqualTo(150000);
        assertThat(paymentResponse.getStatus()).isEqualTo(PaymentResponse.PaymentStatus.SUCCESS);

        // ========== 5단계: 최종 상태 검증 ==========

        // 좌석이 RESERVED(확정) 상태인지 확인
        Seat confirmedSeat = seatRepository.findByConcertDateAndSeatNumber(concertDate, seatNumber).orElseThrow();
        assertThat(confirmedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        // 예약이 CONFIRMED 상태인지 확인
        Reservation confirmedReservation = reservationRepository
                .findByUserIdAndConcertDateAndSeatNumberAndStatus(userId, concertDate, seatNumber, ReservationStatus.CONFIRMED)
                .orElseThrow();
        assertThat(confirmedReservation.getConfirmedAt()).isNotNull();

        // 결제 내역이 존재하는지 확인
        Optional<Payment> payment = paymentRepository.findByReservationId(confirmedReservation.getId());
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.get().getAmount()).isEqualTo(150000L);

        // 포인트가 차감되었는지 확인 (500000 - 150000 = 350000)
        assertThat(paymentResponse.getRemainingPoints()).isEqualTo(350000);

        // 토큰이 EXPIRED 상태인지 확인 (결제 완료 후 토큰 만료)
        QueueToken expiredToken = queueTokenRepository.findByToken(token).orElseThrow();
        assertThat(expiredToken.getStatus()).isEqualTo(TokenStatus.EXPIRED);
    }
}
