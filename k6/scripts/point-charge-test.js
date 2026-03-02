import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 커스텀 메트릭
const chargeFailed = new Rate('charge_failed');
const chargeDuration = new Trend('charge_duration');
const balanceCheckDuration = new Trend('balance_check_duration');
const concurrencyErrors = new Counter('point_concurrency_errors');

/**
 * 시나리오 4: 포인트 동시 충전 테스트 (Load Test)
 *
 * 목적: 동일 사용자에 대한 동시 포인트 충전 시 잔액 정합성 검증
 * 핵심: 낙관적 락 기반 동시성 제어 검증
 *
 * 실행: k6 run k6/scripts/point-charge-test.js
 */
export const options = {
    scenarios: {
        // 시나리오 A: 서로 다른 사용자 충전 (병렬 처리 성능)
        multi_user_charge: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '30s', target: 100 },
                { duration: '20s', target: 100 },
                { duration: '10s', target: 0 },
            ],
            startTime: '0s',
            tags: { test_type: 'multi_user' },
        },
        // 시나리오 B: 동일 사용자 동시 충전 (동시성 제어 검증)
        same_user_charge: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            startTime: '75s',
            tags: { test_type: 'same_user' },
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        charge_failed: ['rate<0.05'],
    },
};

export default function () {
    const testType = __ENV.K6_SCENARIO_NAME || 'multi_user_charge';
    const isSameUser = testType === 'same_user_charge' || (__ITER % 2 === 0 && __VU <= 50);

    // 동일 사용자 or 개별 사용자
    const userId = isSameUser
        ? 'point-test-shared-user'
        : `point-test-user-${__VU}-${__ITER}`;

    group('포인트 충전', function () {
        const amount = Math.floor(Math.random() * 9000) + 1000; // 1,000 ~ 10,000

        const res = http.post(
            `${BASE_URL}/points/charge`,
            JSON.stringify({
                userId: userId,
                amount: amount,
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'POST /points/charge' },
            }
        );

        const isSuccess = check(res, {
            '충전 성공 (200)': (r) => r.status === 200,
            '서버 에러 없음 (5xx)': (r) => r.status < 500,
            '응답시간 1초 미만': (r) => r.timings.duration < 1000,
        });

        chargeDuration.add(res.timings.duration);

        if (!isSuccess) {
            chargeFailed.add(true);
            if (res.status === 409 || res.status === 500) {
                concurrencyErrors.add(1);
            }
        } else {
            chargeFailed.add(false);
        }
    });

    // 잔액 조회
    group('포인트 잔액 조회', function () {
        const balanceRes = http.get(
            `${BASE_URL}/points/balance?userId=${userId}`,
            {
                tags: { name: 'GET /points/balance' },
            }
        );

        check(balanceRes, {
            '잔액 조회 성공 (200)': (r) => r.status === 200,
            '잔액이 0 이상': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.balance >= 0;
                } catch {
                    return false;
                }
            },
        });

        balanceCheckDuration.add(balanceRes.timings.duration);
    });

    sleep(Math.random() * 0.3);
}

export function handleSummary(data) {
    return {
        'k6/results/point-charge-result.json': JSON.stringify(data, null, 2),
    };
}
