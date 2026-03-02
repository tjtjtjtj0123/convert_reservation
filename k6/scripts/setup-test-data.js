import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_USERS = parseInt(__ENV.TOTAL_USERS) || 500;
const POINT_AMOUNT = parseInt(__ENV.POINT_AMOUNT) || 100000;
const CONCERT_DATE = __ENV.CONCERT_DATE || (() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().split('T')[0];
})();

export const options = {
    vus: 10,
    iterations: TOTAL_USERS,
    thresholds: {
        http_req_failed: ['rate<0.05'],
    },
};

/**
 * 테스트 데이터 사전 세팅 스크립트
 * - 테스트 사용자 생성 및 포인트 충전
 *
 * 실행: k6 run k6/scripts/setup-test-data.js
 */
export default function () {
    const userId = `load-test-user-${__VU}-${__ITER}`;

    // 1. 포인트 충전 (각 사용자에게 100,000P)
    const chargeRes = http.post(
        `${BASE_URL}/points/charge`,
        JSON.stringify({
            userId: userId,
            amount: POINT_AMOUNT,
        }),
        {
            headers: { 'Content-Type': 'application/json' },
        }
    );

    check(chargeRes, {
        '포인트 충전 성공': (r) => r.status === 200,
    });

    sleep(0.1);
}
