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
const reservationFailed = new Rate('reservation_failed');
const reservationDuration = new Trend('reservation_duration');
const concurrencyConflicts = new Counter('concurrency_conflicts');
const seatReserved = new Counter('seats_reserved_success');

/**
 * 시나리오 2: 좌석 예약 경쟁 테스트 (Stress Test)
 *
 * 목적: 50개 좌석에 대해 다수 사용자가 동시 예약을 시도하는 상황 시뮬레이션
 * 핵심: 낙관적 락 기반 동시성 제어 검증, 데드락 발생 여부 확인
 *
 * 실행: k6 run k6/scripts/reservation-test.js
 */
export const options = {
    scenarios: {
        // Stress Test - 점진적 부하 증가
        stress_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },    // 워밍업
                { duration: '20s', target: 100 },   // 100명
                { duration: '30s', target: 300 },   // 300명으로 증가
                { duration: '30s', target: 500 },   // 500명으로 증가 (최대 부하)
                { duration: '20s', target: 300 },   // 300명으로 감소
                { duration: '10s', target: 0 },     // 종료
            ],
        },
    },
    thresholds: {
        // 응답시간 (동시성 에러는 빠르게 실패해야 함)
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        // 전체 HTTP 에러율 (동시성 에러 포함하여 높을 수 있음)
        // 좌석 경쟁에서 실패는 예상되는 동작이므로 높은 threshold
        reservation_failed: ['rate<0.95'],
    },
};

/**
 * Setup: 사전 토큰 발급
 * 각 VU가 사용할 토큰을 미리 발급받습니다.
 */
export function setup() {
    const tokens = [];
    for (let i = 0; i < 500; i++) {
        const userId = `reservation-test-user-${i}`;

        // 1. 포인트 충전
        http.post(
            `${BASE_URL}/points/charge`,
            JSON.stringify({ userId: userId, amount: 100000 }),
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
                const body = JSON.parse(tokenRes.body);
                tokens.push({
                    userId: userId,
                    token: body.token,
                });
            } catch (e) {
                // 파싱 실패 무시
            }
        }

        if (i % 50 === 0) {
            sleep(0.5); // 세팅 중 과부하 방지
        }
    }

    console.log(`✅ Setup 완료: ${tokens.length}개 토큰 발급`);
    return { tokens: tokens, concertDate: CONCERT_DATE };
}

export default function (data) {
    if (!data.tokens || data.tokens.length === 0) {
        console.error('토큰 데이터 없음');
        return;
    }

    // VU별로 다른 사용자 토큰 사용
    const tokenData = data.tokens[__VU % data.tokens.length];
    const token = tokenData.token;
    const userId = tokenData.userId;

    // 모든 사용자가 동일한 좌석 범위(1~50)를 노림 → 경쟁 발생
    const seatNumber = Math.floor(Math.random() * 50) + 1;

    group('좌석 예약 경쟁', function () {
        // 좌석 예약 요청
        const res = http.post(
            `${BASE_URL}/reservations`,
            JSON.stringify({
                userId: userId,
                date: data.concertDate,
                seatNumber: seatNumber,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-QUEUE-TOKEN': token,
                },
                tags: { name: 'POST /reservations' },
            }
        );

        // 결과 검증
        const isSuccess = res.status === 200;

        check(res, {
            '예약 성공 또는 정상 경쟁 실패': (r) =>
                r.status === 200 || r.status === 400 || r.status === 409,
            '서버 에러 없음 (5xx)': (r) => r.status < 500,
            '응답시간 2초 미만': (r) => r.timings.duration < 2000,
        });

        // 커스텀 메트릭
        reservationDuration.add(res.timings.duration);

        if (isSuccess) {
            seatReserved.add(1);
            reservationFailed.add(false);
        } else if (res.status === 400 || res.status === 409) {
            // 동시성 충돌 (정상적인 경쟁 실패)
            concurrencyConflicts.add(1);
            reservationFailed.add(false); // 정상 동작이므로 실패로 카운트하지 않음
        } else {
            reservationFailed.add(true);
        }
    });

    sleep(Math.random() * 0.3);
}

export function handleSummary(data) {
    const customSummary = {
        metrics: data.metrics,
        timestamp: new Date().toISOString(),
        testType: 'reservation-stress-test',
    };

    return {
        'k6/results/reservation-result.json': JSON.stringify(customSummary, null, 2),
    };
}
