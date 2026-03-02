import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 커스텀 메트릭
const tokenIssueFailed = new Rate('token_issue_failed');
const tokenIssueDuration = new Trend('token_issue_duration');

/**
 * 시나리오 1: 대기열 토큰 발급 폭주 테스트 (Peak Test)
 *
 * 목적: 인기 콘서트 티켓 오픈 시 수천 명이 동시에 대기열 토큰을 발급받는 상황 시뮬레이션
 * 관찰 지표: 응답시간 p95, 에러율, Redis 연결 수
 *
 * 실행: k6 run k6/scripts/queue-token-test.js
 */
export const options = {
    scenarios: {
        // 시나리오 A: Load Test - 기본 부하
        load_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 },   // 10초간 100명까지 증가
                { duration: '30s', target: 100 },   // 30초간 100명 유지
                { duration: '10s', target: 0 },     // 10초간 0명까지 감소
            ],
            startTime: '0s',
            tags: { test_type: 'load' },
        },
        // 시나리오 B: Peak Test - 순간 폭주
        peak_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 500 },    // 5초만에 500명 폭주
                { duration: '30s', target: 500 },   // 30초간 500명 유지
                { duration: '5s', target: 1000 },   // 5초만에 1000명까지 증가
                { duration: '20s', target: 1000 },  // 20초간 1000명 유지
                { duration: '10s', target: 0 },     // 10초간 감소
            ],
            startTime: '55s',  // Load Test 끝난 후 시작
            tags: { test_type: 'peak' },
        },
    },
    thresholds: {
        // 전체 요청 중 에러율 1% 미만
        http_req_failed: ['rate<0.01'],
        // p95 응답시간 500ms 미만
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        // 토큰 발급 실패율 1% 미만
        token_issue_failed: ['rate<0.01'],
    },
};

export default function () {
    const userId = `user-${__VU}-${__ITER}-${Date.now()}`;

    // 대기열 토큰 발급 요청
    const res = http.post(
        `${BASE_URL}/queue/token`,
        JSON.stringify({ userId: userId }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { name: 'POST /queue/token' },
        }
    );

    // 결과 검증
    const isSuccess = check(res, {
        '토큰 발급 성공 (200)': (r) => r.status === 200,
        '응답에 토큰 포함': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.token !== undefined && body.token !== null;
            } catch {
                return false;
            }
        },
        '응답시간 1초 미만': (r) => r.timings.duration < 1000,
    });

    // 커스텀 메트릭 기록
    tokenIssueFailed.add(!isSuccess);
    tokenIssueDuration.add(res.timings.duration);

    // 토큰 발급 후 상태 조회 (폴링 시뮬레이션)
    if (res.status === 200) {
        try {
            const body = JSON.parse(res.body);
            if (body.token) {
                sleep(1); // 1초 후 상태 조회

                const statusRes = http.get(`${BASE_URL}/queue/status`, {
                    headers: {
                        'X-QUEUE-TOKEN': body.token,
                    },
                    tags: { name: 'GET /queue/status' },
                });

                check(statusRes, {
                    '상태 조회 성공 (200)': (r) => r.status === 200,
                    '대기 상태 확인': (r) => {
                        try {
                            const statusBody = JSON.parse(r.body);
                            return statusBody.status !== undefined;
                        } catch {
                            return false;
                        }
                    },
                });
            }
        } catch (e) {
            // JSON 파싱 실패 무시
        }
    }

    sleep(Math.random() * 0.5); // 0~0.5초 랜덤 대기
}

export function handleSummary(data) {
    return {
        'k6/results/queue-token-result.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}

function textSummary(data, opts) {
    // k6 기본 요약 출력 (내장)
    return '';
}
