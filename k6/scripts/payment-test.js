import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_DATE = __ENV.CONCERT_DATE || (() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().split('T')[0];
})();

// 커스텀 메트릭
const paymentFailed = new Rate('payment_failed');
const paymentDuration = new Trend('payment_duration');
const paymentSuccess = new Counter('payment_success_count');

/**
 * 시나리오 3: 결제 처리 부하 테스트 (Load Test)
 *
 * 목적: 임시 예약된 좌석에 대한 대량 결제 요청 처리 능력 검증
 * 핵심: 포인트 차감 + 예약 확정 + Kafka 이벤트 발행의 복합 트랜잭션 부하
 *
 * 실행: k6 run k6/scripts/payment-test.js
 */
export const options = {
    scenarios: {
        load_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 50 },    // 워밍업
                { duration: '60s', target: 100 },   // 100명 유지
                { duration: '30s', target: 200 },   // 200명으로 증가
                { duration: '60s', target: 200 },   // 200명 유지
                { duration: '15s', target: 0 },     // 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.05'],
        payment_failed: ['rate<0.05'],
    },
};

/**
 * Setup: 좌석 예약 후 결제 대기 데이터 준비
 */
export function setup() {
    const testData = [];

    // 테스트 사용자별 좌석 예약 (각 사용자가 고유 좌석을 점유)
    for (let i = 0; i < 50; i++) {
        const userId = `payment-test-user-${i}`;
        const seatNumber = i + 1;

        // 1. 포인트 충전
        const chargeRes = http.post(
            `${BASE_URL}/points/charge`,
            JSON.stringify({ userId: userId, amount: 500000 }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        // 2. 토큰 발급
        const tokenRes = http.post(
            `${BASE_URL}/queue/token`,
            JSON.stringify({ userId: userId }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (tokenRes.status === 200) {
            try {
                const tokenBody = JSON.parse(tokenRes.body);
                const token = tokenBody.token;

                // 3. 좌석 예약 (임시)
                sleep(0.2);
                const reserveRes = http.post(
                    `${BASE_URL}/reservations`,
                    JSON.stringify({
                        userId: userId,
                        date: CONCERT_DATE,
                        seatNumber: seatNumber,
                    }),
                    {
                        headers: {
                            'Content-Type': 'application/json',
                            'X-QUEUE-TOKEN': token,
                        },
                    }
                );

                if (reserveRes.status === 200) {
                    testData.push({
                        userId: userId,
                        token: token,
                        seatNumber: seatNumber,
                        date: CONCERT_DATE,
                    });
                }
            } catch (e) {
                // 무시
            }
        }

        if (i % 10 === 0) sleep(0.5);
    }

    console.log(`✅ Setup 완료: ${testData.length}개 예약 데이터 준비`);
    return { testData: testData };
}

export default function (data) {
    if (!data.testData || data.testData.length === 0) {
        console.error('테스트 데이터 없음');
        return;
    }

    // VU별로 다른 예약 데이터 사용
    const entry = data.testData[__VU % data.testData.length];

    group('결제 처리', function () {
        const res = http.post(
            `${BASE_URL}/payment`,
            JSON.stringify({
                userId: entry.userId,
                seatNumber: entry.seatNumber,
                date: entry.date,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-QUEUE-TOKEN': entry.token,
                },
                tags: { name: 'POST /payment' },
            }
        );

        const isSuccess = check(res, {
            '결제 성공 또는 이미 처리됨': (r) =>
                r.status === 200 || r.status === 400,
            '서버 에러 없음 (5xx)': (r) => r.status < 500,
            '응답시간 3초 미만': (r) => r.timings.duration < 3000,
        });

        // 커스텀 메트릭
        paymentDuration.add(res.timings.duration);
        if (res.status === 200) {
            paymentSuccess.add(1);
            paymentFailed.add(false);
        } else if (res.status >= 500) {
            paymentFailed.add(true);
        } else {
            paymentFailed.add(false);
        }
    });

    sleep(Math.random() * 1);
}

export function handleSummary(data) {
    return {
        'k6/results/payment-result.json': JSON.stringify(data, null, 2),
    };
}
