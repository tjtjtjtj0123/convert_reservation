#!/bin/bash
# =====================================================
# 부하 테스트 실행 스크립트
# =====================================================
# 사용법:
#   ./k6/run-tests.sh [scenario]
#
# 시나리오:
#   all       - 전체 테스트 실행
#   setup     - 테스트 데이터 세팅
#   queue     - 대기열 토큰 발급 테스트
#   reserve   - 좌석 예약 경쟁 테스트
#   payment   - 결제 부하 테스트
#   point     - 포인트 충전 테스트
#   e2e       - 전체 플로우 테스트
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="${SCRIPT_DIR}/results"

# 결과 디렉토리 생성
mkdir -p "${RESULTS_DIR}"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  🔥 콘서트 예약 서비스 부하 테스트${NC}"
echo -e "${BLUE}================================================${NC}"
echo -e "  Base URL: ${GREEN}${BASE_URL}${NC}"
echo -e "  Results:  ${GREEN}${RESULTS_DIR}${NC}"
echo ""

# 헬스체크
check_health() {
    echo -e "${YELLOW}🏥 서비스 헬스체크...${NC}"
    HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" 2>/dev/null || echo "000")
    if [ "$HEALTH" != "200" ]; then
        echo -e "${RED}❌ 서비스가 응답하지 않습니다. (HTTP ${HEALTH})${NC}"
        echo -e "${YELLOW}   docker-compose -f docker-compose.loadtest.yml up -d 를 먼저 실행해주세요.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ 서비스 정상 (HTTP 200)${NC}"
    echo ""
}

# k6 설치 확인
check_k6() {
    if ! command -v k6 &> /dev/null; then
        echo -e "${RED}❌ k6가 설치되어 있지 않습니다.${NC}"
        echo -e "${YELLOW}   brew install k6 로 설치해주세요.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ k6 버전: $(k6 version)${NC}"
    echo ""
}

run_test() {
    local name=$1
    local script=$2
    local description=$3

    echo -e "${BLUE}────────────────────────────────────────${NC}"
    echo -e "${BLUE}  🚀 ${description}${NC}"
    echo -e "${BLUE}────────────────────────────────────────${NC}"

    k6 run \
        --env BASE_URL="${BASE_URL}" \
        "${SCRIPT_DIR}/scripts/${script}" \
        2>&1 | tee "${RESULTS_DIR}/${name}-$(date +%Y%m%d_%H%M%S).log"

    echo ""
    echo -e "${GREEN}✅ ${description} 완료${NC}"
    echo ""
}

SCENARIO="${1:-all}"

check_k6
check_health

case $SCENARIO in
    setup)
        run_test "setup" "setup-test-data.js" "테스트 데이터 세팅"
        ;;
    queue)
        run_test "queue" "queue-token-test.js" "시나리오 1: 대기열 토큰 발급 (Peak Test)"
        ;;
    reserve)
        run_test "reservation" "reservation-test.js" "시나리오 2: 좌석 예약 경쟁 (Stress Test)"
        ;;
    payment)
        run_test "payment" "payment-test.js" "시나리오 3: 결제 부하 (Load Test)"
        ;;
    point)
        run_test "point" "point-charge-test.js" "시나리오 4: 포인트 충전 (Load Test)"
        ;;
    e2e)
        run_test "e2e" "e2e-flow-test.js" "시나리오 5: 전체 플로우 (E2E Test)"
        ;;
    all)
        echo -e "${YELLOW}📋 전체 테스트 순차 실행${NC}"
        echo ""
        run_test "setup" "setup-test-data.js" "테스트 데이터 세팅"
        sleep 3
        run_test "queue" "queue-token-test.js" "시나리오 1: 대기열 토큰 발급 (Peak Test)"
        sleep 5
        run_test "point" "point-charge-test.js" "시나리오 4: 포인트 충전 (Load Test)"
        sleep 5
        run_test "reservation" "reservation-test.js" "시나리오 2: 좌석 예약 경쟁 (Stress Test)"
        sleep 5
        run_test "payment" "payment-test.js" "시나리오 3: 결제 부하 (Load Test)"
        sleep 5
        run_test "e2e" "e2e-flow-test.js" "시나리오 5: 전체 플로우 (E2E Test)"
        ;;
    *)
        echo -e "${RED}❌ 알 수 없는 시나리오: ${SCENARIO}${NC}"
        echo ""
        echo "사용법: $0 [scenario]"
        echo "  all, setup, queue, reserve, payment, point, e2e"
        exit 1
        ;;
esac

echo -e "${BLUE}================================================${NC}"
echo -e "${GREEN}  🎉 테스트 완료!${NC}"
echo -e "${BLUE}  결과 파일: ${RESULTS_DIR}/${NC}"
echo -e "${BLUE}================================================${NC}"
