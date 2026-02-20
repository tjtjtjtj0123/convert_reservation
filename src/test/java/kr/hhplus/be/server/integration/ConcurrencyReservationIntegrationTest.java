package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.model.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import kr.hhplus.be.server.reservation.application.service.ReservationService;
import kr.hhplus.be.server.reservation.domain.model.ReservationStatus;
import kr.hhplus.be.server.reservation.domain.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트 (Redis 분산락 기반)
 * 다중 유저가 동시에 같은 좌석을 예약 요청할 때,
 * 오직 한 명만 성공하는지 검증한다.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("[통합] 다중 유저 동시 좌석 예약 - Redis 분산락 동시성 제어 검증")
class ConcurrencyReservationIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private static final String CONCERT_DATE = "2026-08-10";
    private static final int TARGET_SEAT = 1;
    private static final int CONCURRENT_USERS = 10;

    @Test
    @DisplayName("10명의 유저가 동시에 같은 좌석을 예약하면, 1명만 성공한다")
    void concurrentReservation_OnlyOneSucceeds() throws InterruptedException {
        // ========== Given: 좌석 데이터 및 각 유저에게 토큰 발급 ==========
        seatRepository.save(new Seat(CONCERT_DATE, TARGET_SEAT));

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            String userId = "concurrent-user-" + String.format("%03d", i);
            QueueTokenResponse tokenResponse = queueService.issueToken(new QueueTokenRequest(userId));
            tokens.add(tokenResponse.getToken());
        }

        // ========== When: 동시에 같은 좌석 예약 요청 ==========
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> successUsers = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int idx = i;
            final String userId = "concurrent-user-" + String.format("%03d", idx);
            final String token = tokens.get(idx);

            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    SeatReserveRequest request = new SeatReserveRequest(userId, CONCERT_DATE, TARGET_SEAT);
                    reservationService.reserveSeat(request, token);

                    successCount.incrementAndGet();
                    successUsers.add(userId);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // ========== Then: 1명만 성공, 나머지는 실패 ==========
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(CONCURRENT_USERS - 1);
        assertThat(successUsers).hasSize(1);

        // DB에서 좌석 상태 확인 - TEMP_HELD 상태여야 함
        Seat seat = seatRepository.findByConcertDateAndSeatNumber(CONCERT_DATE, TARGET_SEAT).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.TEMP_HELD);
        assertThat(seat.getReservedUserId()).isEqualTo(successUsers.get(0));

        // 예약 레코드가 1건만 TEMP_HELD 상태인지 확인
        long tempHeldCount = 0;
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            String userId = "concurrent-user-" + String.format("%03d", i);
            if (reservationRepository.findByUserIdAndConcertDateAndSeatNumberAndStatus(
                    userId, CONCERT_DATE, TARGET_SEAT, ReservationStatus.TEMP_HELD).isPresent()) {
                tempHeldCount++;
            }
        }
        assertThat(tempHeldCount).isEqualTo(1);
    }
}
