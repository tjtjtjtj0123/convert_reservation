import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_DATE = __ENV.CONCERT_DATE || (() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().split('T')[0];
})();

// 커스텀 메트릭
const flowCompleted = new Rate('flow_completed');
const totalFlowDuration = new Trend('total_flow_duration');
const stepDuration = {
    token: new Trend('step_token_duration'),
    status: new Trend('step_status_duration'),
    dates: new Trend('step_dates_duration'),
    seats: new Trend('step_seats_duration'),
    reserve: new Trend('step_reserve_duration'),
    payment: new Trend('step_payment_duration'),
};

/**
 * 시나리오 5: 전체 사용자 플로우 E2E 테스트 (Load Test)
 *
 * 목적: 실제 사용자 행동 패턴을 시뮬레이션하여 전체 플로우의 안정성 검증
 * 흐름: 토큰 발급 → 상태 조회 → 날짜 조회 → 좌석 조회 → 예약 → 결제
 *
 * 실행: k6 run k6/scripts/e2e-flow-test.js
 */
export const options = {
    scenarios: {
        e2e_flow: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 30 },    // 워밍업
                { duration: '60s', target: 50 },    // 50명 유지
                { duration: '60s', target: 100 },   // 100명 증가
                { duration: '120s', target: 100 },  // 100명 유지 (메인 테스트)
                { duration: '30s', target: 50 },    // 50명으로 감소
                { duration: '15s', target: 0 },     // 종료
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.10'],  // E2E에서 일부 실패 허용
        http_req_duration: ['p(95)<2000'],
        flow_completed: ['rate>0.30'],   // 전체 플로우 30% 이상 완료
    },
};

export default function () {
    const userId = `e2e-user-${__VU}-${__ITER}-${Date.now()}`;
    const flowStart = Date.now();
    let token = null;
    let flowSuccess = true;

    // ============================================
    // Step 1: 포인트 충전 (사전 준비)
    // ============================================
    group('Step 0: 포인트 충전', function () {
        const res = http.post(
            `${BASE_URL}/points/charge`,
            JSON.stringify({ userId: userId, amount: 100000 }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'E2E - POST /points/charge' },
            }
        );

        if (!check(res, { '충전 성공': (r) => r.status === 200 })) {
            flowSuccess = false;
        }
    });

    if (!flowSuccess) {
        flowCompleted.add(false);
        return;
    }

    // ============================================
    // Step 1: 대기열 토큰 발급
    // ============================================
    group('Step 1: 대기열 토큰 발급', function () {
        const res = http.post(
            `${BASE_URL}/queue/token`,
            JSON.stringify({ userId: userId }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'E2E - POST /queue/token' },
            }
        );

        stepDuration.token.add(res.timings.duration);

        if (check(res, { '토큰 발급 성공': (r) => r.status === 200 })) {
            try {
                const body = JSON.parse(res.body);
                token = body.token;
            } catch (e) {
                flowSuccess = false;
            }
        } else {
            flowSuccess = false;
        }
    });

    if (!flowSuccess || !token) {
        flowCompleted.add(false);
        return;
    }

    sleep(1); // 사용자 대기 시뮬레이션

    // ============================================
    // Step 2: 대기열 상태 조회 (폴링)
    // ============================================
    group('Step 2: 대기열 상태 조회', function () {
        const res = http.get(`${BASE_URL}/queue/status`, {
            headers: { 'X-QUEUE-TOKEN': token },
            tags: { name: 'E2E - GET /queue/status' },
        });

        stepDuration.status.add(res.timings.duration);

        check(res, {
            '상태 조회 성공': (r) => r.status === 200,
        });
    });

    sleep(0.5);

    // ============================================
    // Step 3: 예약 가능 날짜 조회
    // ============================================
    group('Step 3: 예약 가능 날짜 조회', function () {
        const res = http.get(`${BASE_URL}/concerts/available-dates`, {
            headers: { 'X-QUEUE-TOKEN': token },
            tags: { name: 'E2E - GET /concerts/available-dates' },
        });

        stepDuration.dates.add(res.timings.duration);

        if (!check(res, { '날짜 조회 성공': (r) => r.status === 200 })) {
            flowSuccess = false;
        }
    });

    if (!flowSuccess) {
        flowCompleted.add(false);
        return;
    }

    sleep(0.5);

    // ============================================
    // Step 4: 좌석 목록 조회
    // ============================================
    group('Step 4: 좌석 목록 조회', function () {
        const res = http.get(
            `${BASE_URL}/concerts/seats?date=${CONCERT_DATE}`,
            {
                headers: { 'X-QUEUE-TOKEN': token },
                tags: { name: 'E2E - GET /concerts/seats' },
            }
        );

        stepDuration.seats.add(res.timings.duration);

        check(res, {
            '좌석 조회 성공': (r) => r.status === 200,
        });
    });

    sleep(1); // 사용자가 좌석을 고르는 시간

    // ============================================
    // Step 5: 좌석 임시 예약
    // ============================================
    const seatNumber = Math.floor(Math.random() * 50) + 1;

    group('Step 5: 좌석 임시 예약', function () {
        const res = http.post(
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
                tags: { name: 'E2E - POST /reservations' },
            }
        );

        stepDuration.reserve.add(res.timings.duration);

        if (!check(res, {
            '예약 성공 또는 경쟁 실패': (r) => r.status === 200 || r.status === 400,
        })) {
            flowSuccess = false;
        }

        // 예약 실패 시 (이미 예약된 좌석) 플로우 중단
        if (res.status !== 200) {
            flowSuccess = false;
        }
    });

    if (!flowSuccess) {
        flowCompleted.add(false);
        return;
    }

    sleep(2); // 사용자가 결제 정보를 입력하는 시간

    // ============================================
    // Step 6: 결제
    // ============================================
    group('Step 6: 결제', function () {
        const res = http.post(
            `${BASE_URL}/payment`,
            JSON.stringify({
                userId: userId,
                seatNumber: seatNumber,
                date: CONCERT_DATE,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-QUEUE-TOKEN': token,
                },
                tags: { name: 'E2E - POST /payment' },
            }
        );

        stepDuration.payment.add(res.timings.duration);

        if (!check(res, { '결제 성공': (r) => r.status === 200 })) {
            flowSuccess = false;
        }
    });

    // 최종 결과
    const flowDuration = Date.now() - flowStart;
    totalFlowDuration.add(flowDuration);
    flowCompleted.add(flowSuccess);

    if (flowSuccess) {
        sleep(1);
    }
}

export function handleSummary(data) {
    return {
        'k6/results/e2e-flow-result.json': JSON.stringify(data, null, 2),
    };
}
