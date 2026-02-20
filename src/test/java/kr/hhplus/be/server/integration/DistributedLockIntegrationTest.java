package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.domain.model.Seat;
import kr.hhplus.be.server.concert.domain.model.SeatStatus;
import kr.hhplus.be.server.concert.domain.repository.SeatRepository;
import kr.hhplus.be.server.point.application.service.PointService;
import kr.hhplus.be.server.point.domain.model.PointBalance;
import kr.hhplus.be.server.point.domain.repository.PointBalanceRepository;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeRequest;
import kr.hhplus.be.server.queue.application.service.QueueService;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenRequest;
import kr.hhplus.be.server.queue.interfaces.api.dto.QueueTokenResponse;
import kr.hhplus.be.server.reservation.application.service.ReservationService;
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
 * Redis 분산락 통합 테스트
 * 
 * 검증 시나리오:
 * 1. 좌석 예약 - 동일 좌석 동시 예약 시 1명만 성공
 * 2. 포인트 충전 - 동일 유저 동시 충전 시 정확한 금액 반영
 * 3. 포인트 차감 - 동시 차감 시 잔액 음수 방지
 * 4. 서로 다른 좌석 동시 예약 - 모두 성공 (락 키가 다름)
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("[통합] Redis 분산락 기반 동시성 제어 검증")
class DistributedLockIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PointService pointService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    // =====================================================================
    // 1. 좌석 예약 동시성 테스트
    // =====================================================================

    @Test
    @DisplayName("10명이 동시에 같은 좌석 예약 → 분산락으로 1명만 성공")
    void concurrentSeatReservation_WithDistributedLock_OnlyOneSucceeds() throws InterruptedException {
        // Given
        String concertDate = "2026-09-01";
        int seatNumber = 1;
        int userCount = 10;

        seatRepository.save(new Seat(concertDate, seatNumber));

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            String userId = "dl-user-" + String.format("%03d", i);
            QueueTokenResponse tokenRes = queueService.issueToken(new QueueTokenRequest(userId));
            tokens.add(tokenRes.getToken());
        }

        // When: 동시에 같은 좌석 예약
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> successUsers = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < userCount; i++) {
            final int idx = i;
            final String userId = "dl-user-" + String.format("%03d", idx);
            final String token = tokens.get(idx);

            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    SeatReserveRequest request = new SeatReserveRequest(userId, concertDate, seatNumber);
                    reservationService.reserveSeat(request, token);

                    successCount.incrementAndGet();
                    successUsers.add(userId);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(userCount - 1);

        Seat seat = seatRepository.findByConcertDateAndSeatNumber(concertDate, seatNumber).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.TEMP_HELD);
        assertThat(seat.getReservedUserId()).isEqualTo(successUsers.get(0));
    }

    // =====================================================================
    // 2. 서로 다른 좌석 동시 예약 (병렬 처리 가능)
    // =====================================================================

    @Test
    @DisplayName("서로 다른 좌석을 동시에 예약 → 분산락 키가 달라 모두 성공")
    void concurrentDifferentSeatReservation_AllSucceed() throws InterruptedException {
        // Given
        String concertDate = "2026-09-02";
        int userCount = 5;

        // 각 유저마다 다른 좌석 준비
        for (int i = 1; i <= userCount; i++) {
            seatRepository.save(new Seat(concertDate, i));
        }

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            String userId = "diff-seat-user-" + String.format("%03d", i);
            QueueTokenResponse tokenRes = queueService.issueToken(new QueueTokenRequest(userId));
            tokens.add(tokenRes.getToken());
        }

        // When
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < userCount; i++) {
            final int idx = i;
            final int targetSeat = idx + 1;
            final String userId = "diff-seat-user-" + String.format("%03d", idx);
            final String token = tokens.get(idx);

            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    SeatReserveRequest request = new SeatReserveRequest(userId, concertDate, targetSeat);
                    reservationService.reserveSeat(request, token);

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // Then: 모든 요청 성공 (서로 다른 좌석이므로 락 충돌 없음)
        assertThat(successCount.get()).isEqualTo(userCount);
        assertThat(failCount.get()).isEqualTo(0);
    }

    // =====================================================================
    // 3. 포인트 동시 충전 테스트
    // =====================================================================

    @Test
    @DisplayName("동시에 5건의 포인트 충전 → 분산락으로 순차 처리, 정확한 금액 반영")
    void concurrentPointCharge_WithDistributedLock_ExactAmount() throws InterruptedException {
        // Given
        String userId = "charge-test-user";
        int chargeAmount = 10000;
        int requestCount = 5;
        long expectedBalance = (long) chargeAmount * requestCount;

        // 초기 잔액 0원
        pointBalanceRepository.save(new PointBalance(userId));

        // When
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    pointService.charge(new PointChargeRequest(userId, chargeAmount));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // Then: 모든 충전 성공, 정확한 금액
        assertThat(successCount.get()).isEqualTo(requestCount);
        assertThat(failCount.get()).isEqualTo(0);

        PointBalance finalBalance = pointBalanceRepository.findById(userId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(expectedBalance);
    }

    // =====================================================================
    // 4. 포인트 동시 차감 테스트
    // =====================================================================

    @Test
    @DisplayName("잔액 50,000원에서 동시에 10건 × 10,000원 차감 → 분산락으로 5건만 성공, 잔액 0원")
    void concurrentPointDeduction_WithDistributedLock_PreventNegativeBalance() throws InterruptedException {
        // Given
        String userId = "deduct-test-user";
        long initialBalance = 50000L;
        long deductAmount = 10000L;
        int requestCount = 10;

        PointBalance balance = new PointBalance(userId);
        balance.charge(initialBalance);
        pointBalanceRepository.save(balance);

        // When
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    pointService.usePoint(userId, deductAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // Then: 5건 성공, 5건 실패, 잔액 0원
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        PointBalance finalBalance = pointBalanceRepository.findById(userId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(0L);
    }

    // =====================================================================
    // 5. 충전과 사용 동시 요청 테스트
    // =====================================================================

    @Test
    @DisplayName("동일 유저의 충전과 사용이 동시에 요청 → 분산락으로 순차 처리, 정확한 잔액")
    void concurrentChargeAndUse_WithDistributedLock_ConsistentBalance() throws InterruptedException {
        // Given
        String userId = "charge-use-test-user";
        long initialBalance = 100000L;
        int chargeAmount = 10000;
        long useAmount = 10000L;
        int totalRequests = 10; // 5 충전 + 5 사용

        PointBalance balance = new PointBalance(userId);
        balance.charge(initialBalance);
        pointBalanceRepository.save(balance);

        // 기대 잔액: 100,000 + (5 * 10,000) - (5 * 10,000) = 100,000
        long expectedBalance = initialBalance;

        // When
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch ready = new CountDownLatch(totalRequests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalRequests);

        AtomicInteger chargeSuccess = new AtomicInteger(0);
        AtomicInteger useSuccess = new AtomicInteger(0);

        // 5 충전 요청
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    pointService.charge(new PointChargeRequest(userId, chargeAmount));
                    chargeSuccess.incrementAndGet();
                } catch (Exception e) {
                    // 충전 실패
                } finally {
                    done.countDown();
                }
            });
        }

        // 5 사용 요청
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    pointService.usePoint(userId, useAmount);
                    useSuccess.incrementAndGet();
                } catch (Exception e) {
                    // 사용 실패
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // Then: 모든 충전/사용 성공, 잔액 = 초기값 (같은 수의 충전/사용)
        assertThat(chargeSuccess.get()).isEqualTo(5);
        assertThat(useSuccess.get()).isEqualTo(5);

        PointBalance finalBalance = pointBalanceRepository.findById(userId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(expectedBalance);
    }
}
