package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.point.application.service.PointService;
import kr.hhplus.be.server.point.domain.model.PointBalance;
import kr.hhplus.be.server.point.domain.repository.PointBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포인트 차감 동시성 통합 테스트
 * 같은 유저가 동시에 여러 결제를 시도할 때,
 * 잔액이 음수가 되지 않고 정확히 처리되는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("[통합] 포인트 동시 차감 - 음수 잔액 방지 검증")
class ConcurrentPointDeductionTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    private static final String TEST_USER_ID = "concurrent-point-user";
    private static final long INITIAL_BALANCE = 50000L;
    private static final long DEDUCTION_AMOUNT = 10000L;
    private static final int CONCURRENT_REQUESTS = 10;

    @BeforeEach
    void setUp() {
        // 초기 잔액 50,000원 설정
        PointBalance balance = new PointBalance(TEST_USER_ID);
        balance.charge(INITIAL_BALANCE);
        pointBalanceRepository.save(balance);
    }

    @Test
    @DisplayName("동시에 10건의 포인트 차감 요청 시, 5건만 성공하고 잔액은 0원이 된다")
    void concurrentPointDeduction_PreventNegativeBalance() throws InterruptedException {
        // ========== Given: 초기 잔액 50,000원 ==========
        // 각 요청당 10,000원씩 차감 → 5건만 성공해야 함

        // ========== When: 동시에 10건의 차감 요청 ==========
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    // 포인트 차감 시도
                    pointService.usePoint(TEST_USER_ID, DEDUCTION_AMOUNT);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 잔액 부족 등의 이유로 실패
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

        // ========== Then: 5건만 성공, 5건 실패 ==========
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // DB에서 최종 잔액 확인 - 정확히 0원이어야 함
        PointBalance finalBalance = pointBalanceRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("동시에 3건의 포인트 차감 요청 시, 모두 성공하고 잔액은 20,000원이 된다")
    void concurrentPointDeduction_AllSuccess() throws InterruptedException {
        // ========== Given: 초기 잔액 50,000원 ==========
        int requestCount = 3;
        long expectedBalance = INITIAL_BALANCE - (DEDUCTION_AMOUNT * requestCount);

        // ========== When: 동시에 3건의 차감 요청 ==========
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    pointService.usePoint(TEST_USER_ID, DEDUCTION_AMOUNT);
                    successCount.incrementAndGet();
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

        // ========== Then: 모든 요청 성공 ==========
        assertThat(successCount.get()).isEqualTo(requestCount);
        assertThat(failCount.get()).isEqualTo(0);

        // DB에서 최종 잔액 확인 - 20,000원이어야 함
        PointBalance finalBalance = pointBalanceRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("잔액이 부족한 상태에서 동시 차감 요청 시, 모두 실패한다")
    void concurrentPointDeduction_AllFail_InsufficientBalance() throws InterruptedException {
        // ========== Given: 초기 잔액 5,000원 (부족한 상태) ==========
        PointBalance balance = pointBalanceRepository.findById(TEST_USER_ID).orElseThrow();
        balance.use(45000L); // 잔액을 5,000원으로 감소
        pointBalanceRepository.save(balance);

        int requestCount = 3;

        // ========== When: 동시에 3건의 10,000원 차감 요청 ==========
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    pointService.usePoint(TEST_USER_ID, DEDUCTION_AMOUNT);
                    successCount.incrementAndGet();
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

        // ========== Then: 모든 요청 실패 ==========
        assertThat(successCount.get()).isEqualTo(0);
        assertThat(failCount.get()).isEqualTo(requestCount);

        // DB에서 최종 잔액 확인 - 여전히 5,000원이어야 함
        PointBalance finalBalance = pointBalanceRepository.findById(TEST_USER_ID).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(5000L);
    }
}
