# 🚨 콘서트 예약 서비스 - 장애 대응 매뉴얼

> **작성일**: 2026-03-03  
> **작성자**: 서버팀  
> **문서 버전**: v1.0  
> **대상**: 서버팀, 운영팀, CS팀, 경영진 등 전체 이해관계자

---

## 📋 목차
1. [시스템 전반 구조](#1-시스템-전반-구조)
2. [서비스 수용 가능 트래픽](#2-서비스-수용-가능-트래픽)
3. [장애 등급 분류 체계](#3-장애-등급-분류-체계)
4. [장애 대응 프로세스](#4-장애-대응-프로세스)
5. [예상 장애 시나리오 및 대응](#5-예상-장애-시나리오-및-대응)
6. [예측 불가 장애 대응](#6-예측-불가-장애-대응)
7. [장애 회고 템플릿](#7-장애-회고-템플릿)
8. [비상 연락 체계](#8-비상-연락-체계)

---

## 1. 시스템 전반 구조

### 1.1 아키텍처 다이어그램

```
                    ┌─────────────┐
                    │   Client    │
                    │  (Browser)  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  Spring Boot│
                    │  Application│
                    │  (Java 21)  │
                    └──┬──┬──┬──┬─┘
                       │  │  │  │
          ┌────────────┘  │  │  └────────────┐
          │               │  │               │
    ┌─────▼─────┐  ┌─────▼──▼────┐   ┌──────▼─────┐
    │   MySQL    │  │    Redis    │   │   Kafka    │
    │   8.0      │  │    7.2      │   │ (3 Broker) │
    │            │  │             │   │            │
    │ • 좌석     │  │ • 대기열    │   │ • 결제완료  │
    │ • 예약     │  │   토큰 관리 │   │   이벤트   │
    │ • 결제     │  │ • 분산 락   │   │ • 비동기   │
    │ • 포인트   │  │   (Redisson)│   │   처리     │
    │ • 공연일정 │  │ • 캐시      │   │            │
    └───────────┘  └─────────────┘   └────────────┘
```

### 1.2 도메인별 구성

| 도메인 | 역할 | 핵심 기술 | SPOF 위험도 |
|--------|------|----------|-------------|
| **대기열 (Queue)** | 트래픽 제어, 토큰 관리 | Redis Sorted Set | 🔴 High |
| **콘서트 (Concert)** | 공연 일정/좌석 조회 | MySQL + JPA | 🟡 Medium |
| **예약 (Reservation)** | 좌석 임시 예약 (5분) | MySQL 낙관적 락 | 🔴 High |
| **결제 (Payment)** | 포인트 차감 + 예약 확정 | MySQL + Kafka | 🔴 High |
| **포인트 (Point)** | 포인트 충전/차감 | MySQL 낙관적 락 | 🟡 Medium |

### 1.3 핵심 설정 값

| 설정 항목 | 현재 값 | 비고 |
|-----------|---------|------|
| HikariCP Maximum Pool Size | **3** | ⚠️ 매우 적음 - 부하 시 병목 예상 |
| Connection Timeout | 10,000ms | |
| Max Lifetime | 60,000ms | |
| 좌석 임시 예약 시간 | 5분 | |
| 좌석 수 (공연당) | 50개 | |
| Redis Append Only | 활성화 | 데이터 영속성 보장 |

---

## 2. 서비스 수용 가능 트래픽

### 2.1 API별 예상 처리량 (기본 스펙 기준)

> 📝 아래 수치는 부하 테스트 실행 후 실측 데이터로 업데이트 필요

| API | 예상 TPS | p95 응답시간 | 비고 |
|-----|---------|-------------|------|
| `POST /queue/token` | ~200 TPS | < 300ms | Redis 의존 |
| `GET /queue/status` | ~500 TPS | < 200ms | Redis 조회 |
| `GET /concerts/available-dates` | ~300 TPS | < 150ms | DB 조회 |
| `GET /concerts/seats` | ~200 TPS | < 300ms | DB 조회 |
| `POST /reservations` | ~50 TPS | < 500ms | 낙관적 락 경합 |
| `POST /payment` | ~30 TPS | < 700ms | 복합 트랜잭션 |
| `POST /points/charge` | ~100 TPS | < 300ms | 동시성 제어 |

### 2.2 병목 지점 (사전 예측)

```
⚠️ 주요 병목 예측 (높음 → 낮음)

1. [Critical] DB Connection Pool (max=3)
   → 동시 3개 초과 요청 시 커넥션 대기 발생
   → 부하 집중 시 Connection Timeout 가능
   
2. [High] 좌석 예약 Lock 경합
   → 인기 좌석에 대한 동시 예약 시 낙관적 락 충돌
   → 대부분 재시도 없이 실패 → 사용자 경험 저하
   
3. [High] 결제 트랜잭션 길이
   → 포인트 차감 + 예약 확정 + Kafka 발행이 단일 트랜잭션
   → Kafka 브로커 장애 시 전체 결제 실패 가능
   
4. [Medium] Redis 단일 장애점
   → Redis 장애 시 대기열 전체 기능 마비
   → 분산 락 (Redisson) 불가 → 동시성 제어 실패
```

---

## 3. 장애 등급 분류 체계

### 3.1 등급 정의

| 등급 | 명칭 | 기준 | 대응 SLA | 예시 |
|------|------|------|---------|------|
| 🔴 **P1** | Critical | 전체 서비스 중단 | 15분 내 대응 시작 | 서버 다운, DB 연결 불가 |
| 🟠 **P2** | Major | 핵심 기능 장애 | 30분 내 대응 시작 | 결제 불가, 예약 불가 |
| 🟡 **P3** | Minor | 부가 기능 장애 | 2시간 내 대응 시작 | 랭킹 조회 실패, 알림 지연 |
| 🟢 **P4** | Low | 경미한 이슈 | 다음 업무일 대응 | UI 오류, 로그 누락 |

### 3.2 에스컬레이션 기준

```
P4 → P3: 동일 이슈 10건 이상 반복 발생
P3 → P2: 에러율 5% 초과 또는 사용자 CS 문의 급증
P2 → P1: 서비스 전체 또는 핵심 기능 완전 중단
```

---

## 4. 장애 대응 프로세스

### 4.1 대응 플로우

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  1. 장애    │───▶│  2. 장애    │───▶│  3. 장애    │───▶│  4. 해소    │
│    탐지     │    │  분류/전파  │    │  복구/보고  │    │    통지     │
└─────────────┘    └─────────────┘    └─────────────┘    └──────┬──────┘
                                                                │
                                                         ┌──────▼──────┐
                                                         │  5. 장애    │
                                                         │    회고     │
                                                         └─────────────┘
```

### 4.2 단계별 행동 지침

#### 📌 Step 1: 장애 탐지
- [ ] Health Check 실패 알림 확인 (`/actuator/health`)
- [ ] 에러율 급증 모니터링 (5xx 비율 확인)
- [ ] Slack 채널에 장애 감지 공유

```
🚨 [장애 탐지] YYYY-MM-DD HH:mm
탐지자: @이름
증상: (ex. 결제 API 5xx 에러 급증)
확인 방법: (ex. Grafana 대시보드, Slack Alert)
```

#### 📌 Step 2: 장애 분류 및 전파
- [ ] 장애 등급 판별 (P1/P2/P3/P4)
- [ ] 장애 범위 파악 (영향받는 기능/사용자 수)
- [ ] 관련 채널에 전파

```
🔴 [장애 전파] P1 - 서비스 전체 장애
시각: YYYY-MM-DD HH:mm
영향 범위: 전체 사용자
증상: 모든 API 응답 불가
담당자: @서버팀
현재 상황: 원인 파악 중
```

#### 📌 Step 3: 장애 복구
- [ ] 로그 확인 (Application Log + MySQL Slow Query Log)
- [ ] 즉시 복구 가능 여부 판단
- [ ] 롤백/핫픽스/인프라 조치 중 선택
- [ ] 진행 상황 15분 간격 업데이트

#### 📌 Step 4: 해소 통지
- [ ] 서비스 정상화 확인
- [ ] 관련 채널에 해소 통지
- [ ] CS팀에 고객 안내 요청

```
✅ [장애 해소] P1 해소 완료
시각: YYYY-MM-DD HH:mm
장애 시간: 약 XX분
해소 방법: (ex. DB Connection Pool 확대 + 서비스 재시작)
후속 조치: 장애 회고 진행 예정
```

#### 📌 Step 5: 장애 회고
- [ ] 72시간 내 회고 진행
- [ ] 회고 문서 작성 (아래 템플릿 사용)
- [ ] 재발 방지 액션 아이템 도출

---

## 5. 예상 장애 시나리오 및 대응

### 5.1 🔴 DB Connection Pool 고갈

| 항목 | 내용 |
|------|------|
| **증상** | API 응답 지연 → Timeout → 5xx 에러 급증 |
| **원인** | HikariCP max-pool-size=3으로 동시 요청 수용 불가 |
| **영향도** | P1 - 전체 서비스 장애 |
| **탐지** | HikariCP 메트릭 모니터링, Connection Timeout 로그 |

**즉시 대응:**
```bash
# 1. 현재 DB 커넥션 상태 확인
mysql -u root -proot -e "SHOW PROCESSLIST;"

# 2. 잠금 대기 확인
mysql -u root -proot -e "SELECT * FROM information_schema.INNODB_TRX;"

# 3. 긴급 조치: 애플리케이션 재시작
docker restart app

# 4. 근본 해결: Connection Pool 사이즈 증가 (환경변수)
HIKARI_POOL_SIZE=20 docker-compose -f docker-compose.loadtest.yml up -d app
```

**개선 방안:**
- 단기: HikariCP max-pool-size를 10~20으로 증가
- 중기: Connection Pool 메트릭 모니터링 구축 (Micrometer + Grafana)
- 장기: Read Replica 도입으로 읽기/쓰기 분리

---

### 5.2 🔴 좌석 예약 데드락

| 항목 | 내용 |
|------|------|
| **증상** | 좌석 예약 API 간헐적 실패, MySQL DeadLock 에러 로그 |
| **원인** | 동일 좌석에 대한 동시 예약 시 낙관적 락 충돌, 또는 트랜잭션 내 Lock 순서 불일치 |
| **영향도** | P2 - 예약 기능 장애 |
| **탐지** | `Deadlock found when trying to get lock` 에러 로그 |

**즉시 대응:**
```bash
# 1. DeadLock 상태 확인
mysql -u root -proot -e "SHOW ENGINE INNODB STATUS\G" | grep -A 50 "LATEST DETECTED DEADLOCK"

# 2. 현재 Lock 대기 확인
mysql -u root -proot -e "
SELECT * FROM performance_schema.data_lock_waits;
"

# 3. 장시간 Lock 유지 중인 트랜잭션 강제 종료
mysql -u root -proot -e "
SELECT trx_id, trx_started, trx_mysql_thread_id 
FROM information_schema.INNODB_TRX 
WHERE trx_started < NOW() - INTERVAL 30 SECOND;
"
# KILL <thread_id>; 로 강제 종료
```

**개선 방안:**
- 단기: 재시도 로직 추가 (RetryTemplate 또는 @Retryable)
- 중기: Lock 순서 통일, 트랜잭션 범위 최소화
- 장기: 분산 락(Redisson)으로 전환하여 DB 레벨 Lock 의존도 감소

---

### 5.3 🔴 Redis 장애 (대기열 마비)

| 항목 | 내용 |
|------|------|
| **증상** | 토큰 발급/조회 불가, 분산 락 획득 실패 |
| **원인** | Redis OOM, 네트워크 장애, 프로세스 종료 |
| **영향도** | P1 - 대기열 전체 기능 마비 + 동시성 제어 실패 |
| **탐지** | Redis connection refused 에러, Health Check 실패 |

**즉시 대응:**
```bash
# 1. Redis 상태 확인
docker exec -it redis redis-cli ping
docker exec -it redis redis-cli info memory

# 2. Redis 재시작
docker restart redis

# 3. Redis 메모리 상태 확인
docker exec -it redis redis-cli info memory | grep used_memory_human

# 4. 긴급 시 대기열 우회 (모든 사용자 ACTIVE 처리)
# → application.yml에 대기열 우회 플래그 설정 후 재배포
```

**개선 방안:**
- 단기: Redis Sentinel 구성으로 고가용성 확보
- 중기: Redis Cluster 도입
- 장기: 대기열 장애 시 Fallback 로직 (DB 기반 대기열 or 전수 통과)

---

### 5.4 🟠 Kafka 브로커 장애

| 항목 | 내용 |
|------|------|
| **증상** | 결제 완료 이벤트 발행 실패, 후속 처리 지연 |
| **원인** | Kafka 브로커 다운, 네트워크 분리 |
| **영향도** | P2 - 결제는 성공하나 후속 처리(알림 등) 지연 |
| **탐지** | Kafka producer timeout 에러 로그 |

**즉시 대응:**
```bash
# 1. Kafka 브로커 상태 확인
docker exec -it broker1 kafka-broker-api-versions --bootstrap-server localhost:29092

# 2. 토픽 상태 확인
docker exec -it broker1 kafka-topics --bootstrap-server localhost:29092 --describe

# 3. 컨슈머 그룹 상태 확인
docker exec -it broker1 kafka-consumer-groups --bootstrap-server localhost:29092 --list

# 4. 장애 브로커 재시작
docker restart broker1
```

**개선 방안:**
- 단기: Kafka 발행 실패 시 Outbox 패턴으로 DB에 이벤트 저장 후 재발행
- 중기: Kafka 클러스터 모니터링 (kafka-ui 등)
- 장기: Dead Letter Queue(DLQ) 구성으로 실패 메시지 보존

---

### 5.5 🟠 결제 트랜잭션 타임아웃

| 항목 | 내용 |
|------|------|
| **증상** | 결제 API 응답 지연, 간헐적 timeout |
| **원인** | 포인트 차감 + 예약 확정 + Kafka 발행의 긴 트랜잭션 |
| **영향도** | P2 - 결제 기능 지연/실패 |
| **탐지** | 결제 API p95 > 3초, Transaction timeout 로그 |

**즉시 대응:**
```bash
# 1. 현재 활성 트랜잭션 확인
mysql -u root -proot -e "
SELECT * FROM information_schema.INNODB_TRX 
ORDER BY trx_started ASC;
"

# 2. Slow Query 확인
mysql -u root -proot -e "
SELECT * FROM mysql.slow_log 
ORDER BY start_time DESC LIMIT 20;
"

# 3. 트랜잭션 타임아웃 임시 증가
# application.yml에서 spring.transaction.default-timeout 조정
```

**개선 방안:**
- 단기: 트랜잭션 범위 분리 (결제 확정 → 이벤트 발행을 @TransactionalEventListener로 분리)
- 중기: 비동기 결제 처리 (결제 요청 → 비동기 처리 → 결과 콜백)
- 장기: CQRS 패턴 적용으로 명령과 조회 분리

---

### 5.6 🟡 임시 예약 만료 대량 발생

| 항목 | 내용 |
|------|------|
| **증상** | 사용자 결제 시도 시 "임시 예약 만료" 에러 급증 |
| **원인** | 트래픽 과부하로 결제 처리 지연 → 5분 임시 예약 시간 초과 |
| **영향도** | P3 - 사용자 경험 저하, CS 문의 급증 |
| **탐지** | 예약 만료 에러 로그 급증 |

**즉시 대응:**
1. 임시 예약 만료 시간 연장 (5분 → 10분) 핫픽스
2. 만료 스케줄러 주기 조정

**개선 방안:**
- 단기: 결제 페이지 진입 시 타이머 표시
- 중기: 결제 대기열 도입
- 장기: 예약 만료 전 푸시 알림

---

## 6. 예측 불가 장애 대응

### 6.1 일반 대응 원칙

예측하지 못한 장애가 발생했을 때 **기계적으로 수행할 절차**입니다.

```
1. 🔍 증상 파악 (2분 이내)
   - Health Check 결과 확인
   - 에러 로그 최근 50줄 확인
   - 외부 의존성 (MySQL, Redis, Kafka) 연결 상태 확인

2. 🏷️ 장애 등급 판별 (3분 이내)
   - 영향 범위 파악: 전체 / 특정 API / 특정 사용자
   - 등급 부여: P1 ~ P4

3. 📢 전파 (즉시)
   - Slack #incident 채널에 장애 선언
   - P1/P2: 전체 관계자 태그
   - P3/P4: 담당팀만 태그

4. 🔧 긴급 조치 시도 (15분 이내)
   - 서비스 재시작: docker restart app
   - 롤백: 이전 버전 이미지로 교체
   - 트래픽 차단: 특정 API 일시 중단

5. 📊 상황 업데이트 (15분 간격)
   - 진행 상황을 Slack에 업데이트
   - 원인 파악 여부, 예상 복구 시간 공유
```

### 6.2 비상 명령어 모음

```bash
# ===== 서비스 상태 확인 =====
# 앱 헬스체크
curl http://localhost:8080/actuator/health

# 컨테이너 상태
docker ps -a
docker stats --no-stream

# ===== 로그 확인 =====
# 앱 에러 로그 (최근 100줄)
docker logs app --tail 100 | grep -i "error\|exception"

# MySQL Slow Query
docker exec mysql mysql -u root -proot -e "SHOW GLOBAL STATUS LIKE 'Slow_queries';"

# Redis 메모리
docker exec redis redis-cli info memory

# ===== 긴급 복구 =====
# 전체 서비스 재시작
docker-compose -f docker-compose.loadtest.yml restart

# 앱만 재시작
docker restart app

# MySQL 커넥션 강제 정리
docker exec mysql mysql -u root -proot -e "
SELECT GROUP_CONCAT(CONCAT('KILL ', id, ';') SEPARATOR ' ') 
FROM information_schema.PROCESSLIST 
WHERE command != 'Sleep' AND time > 60;
"

# Redis 플러시 (최후의 수단)
docker exec redis redis-cli FLUSHDB
```

---

## 7. 장애 회고 템플릿

```markdown
## 장애 회고 보고서

### 현상
- **타임라인**
  - [HH:mm] 장애 탐지
  - [HH:mm] 장애 전파
  - [HH:mm] 원인 파악
  - [HH:mm] 복구 완료
- **영향 범위**: (ex. 전체 사용자 예약/결제 불가)
- **고객 영향도**: (ex. 약 X명의 사용자가 Y분간 서비스 이용 불가)
- **비즈니스 임팩트**: (ex. 약 X건의 예약 실패 발생)

### 조치 내용
- **장애 원인**: (상세 기술)
- **해소 타임라인**: (장애 발생 ~ 해소까지)
- **실제 단기 대응책**: (ex. Connection Pool 사이즈 증가 + 재시작)
- **후속 대응 계획**: (ex. 모니터링 구축, 코드 개선)

### 상세 분석 (5-Whys)
1. **Why**: 왜 장애가 발생했는가?
   → (ex. DB Connection Pool 고갈)
2. **Why**: 왜 Connection Pool이 고갈되었는가?
   → (ex. max-pool-size=3으로 설정되어 있었음)
3. **Why**: 왜 3으로 설정되어 있었는가?
   → (ex. 개발 환경 설정이 프로덕션에 그대로 적용됨)
4. **Why**: 왜 개발 설정이 프로덕션에 적용되었는가?
   → (ex. 환경별 설정 분리가 안 되어 있었음)
5. **Why**: 왜 설정 분리가 안 되어 있었는가?
   → (ex. 배포 프로세스에 설정 검증 단계가 없었음)

### 대응 방안 / 액션 아이템
| 구분 | 액션 아이템 | 담당 | 기한 |
|------|-----------|------|------|
| **Short-term** | Connection Pool 사이즈 프로덕션 분리 | 서버팀 | 1일 |
| **Mid-term** | DB Connection 모니터링 대시보드 구축 | 인프라팀 | 1주 |
| **Long-term** | 배포 설정 검증 자동화 (CI/CD) | DevOps팀 | 1개월 |
```

---

## 8. 비상 연락 체계

### 8.1 에스컬레이션 경로

```
장애 감지 → 서버팀 온콜 담당자 (1차)
     ├─ P3/P4 → 담당자 자체 해결
     ├─ P2 → 서버팀 리드 + CS팀 리드 (2차)
     └─ P1 → CTO + 전체 관계자 (3차)
```

### 8.2 체크리스트 (장애 발생 시)

- [ ] 1. 장애 증상 확인 및 로그 수집
- [ ] 2. 장애 등급 판별 (P1~P4)
- [ ] 3. Slack #incident 채널에 장애 선언
- [ ] 4. 영향 범위 파악 (사용자 수, 기능 범위)
- [ ] 5. 즉시 복구 가능 여부 판단
- [ ] 6. 복구 조치 실행
- [ ] 7. 15분 간격 상황 업데이트
- [ ] 8. 복구 완료 확인 및 해소 통지
- [ ] 9. 72시간 내 장애 회고 진행
- [ ] 10. 액션 아이템 등록 및 추적

---

> 💡 **본 매뉴얼은 살아있는 문서입니다.**  
> 장애 발생 및 회고를 통해 지속적으로 업데이트하며, 새로운 장애 시나리오가 발견될 때마다 추가합니다.
