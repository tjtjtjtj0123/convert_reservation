# CH07 - Redis 기반 랭킹 & 대기열 시스템 설계 보고서

## 📋 목차
1. [개요](#개요)
2. [빠른 매진 랭킹 설계](#빠른-매진-랭킹-설계)
3. [Redis 기반 대기열 설계](#redis-기반-대기열-설계)
4. [구현 회고](#구현-회고)

---

## 개요

### 과제 요구사항
- **[필수] 랭킹 시스템**: Redis Sorted Set 기반 빠른 매진 랭킹 설계 및 구현
- **[선택] 비동기 대기열**: Redis 기반 대기열 기능 설계 및 구현

### 기술 스택
- Redis Sorted Set, Set, String
- Spring Data Redis (StringRedisTemplate)
- Redisson (기존 분산락과 함께 사용)
- Testcontainers (Redis 통합 테스트)

---

## 빠른 매진 랭킹 설계

### 1. 설계 의도

콘서트 좌석 예약 시 **어떤 콘서트의 예약이 가장 빠르게 진행되는지** 실시간으로 추적하고 랭킹을 제공하는 기능입니다.

#### 왜 Redis Sorted Set인가?

| 기준 | DB 기반 | Redis Sorted Set |
|------|---------|-----------------|
| 카운트 증가 | `UPDATE ... SET count = count + 1` + 트랜잭션 | `ZINCRBY` 원자적 O(log N) |
| Top N 조회 | `ORDER BY count DESC LIMIT N` + 인덱스 | `ZREVRANGE` O(log N + M) |
| 순위 조회 | `COUNT(*) + 서브쿼리` | `ZREVRANK` O(log N) |
| 동시성 처리 | 비관적/낙관적 락 필요 | 원자적 연산, 락 불필요 |
| 지연 시간 | 수~수십 ms | sub-ms |

### 2. Redis 데이터 구조

```
키: "ranking:soldout" (Sorted Set)

┌──────────────────────────────────────────┐
│  Member (콘서트 날짜)  │  Score (예약 수)  │
├──────────────────────────────────────────┤
│  "2025-12-25"         │  50              │
│  "2025-12-31"         │  30              │
│  "2025-12-24"         │  20              │
└──────────────────────────────────────────┘
```

### 3. 핵심 연산

```
# 좌석 예약 시 (원자적 카운트 증가)
ZINCRBY ranking:soldout 1 "2025-12-25"

# 빠른 매진 Top 10 조회
ZREVRANGE ranking:soldout 0 9 WITHSCORES

# 특정 콘서트 순위 조회
ZREVRANK ranking:soldout "2025-12-25"

# 특정 콘서트 예약 수 조회
ZSCORE ranking:soldout "2025-12-25"
```

### 4. 아키텍처 (Clean Architecture)

```
┌─────────────────────────────────────────────────────────┐
│  Interface Layer                                         │
│  ConcertController                                       │
│  GET /concerts/ranking?topN=10                           │
├─────────────────────────────────────────────────────────┤
│  Application Layer                                       │
│  ConcertRankingService                                   │
│  - onSeatReserved(concertDate) → ZINCRBY                │
│  - getTopRanking(topN) → ZREVRANGE                      │
│  - getConcertRanking(concertDate) → ZREVRANK + ZSCORE   │
├─────────────────────────────────────────────────────────┤
│  Domain Layer                                            │
│  ConcertRankingRepository (Interface)                    │
│  - incrementReservationCount()                           │
│  - getTopRanking()                                       │
│  - getRank() / getScore()                                │
├─────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                    │
│  ConcertRankingRedisRepository                           │
│  → StringRedisTemplate.opsForZSet()                      │
└─────────────────────────────────────────────────────────┘
```

### 5. 호출 흐름

```
사용자 → POST /reservations (좌석 예약)
  │
  ├── ReservationService.reserveSeat()
  │     ├── 1. 토큰 검증
  │     ├── 2. 좌석 조회 & 예약 (분산락)
  │     ├── 3. 예약 엔티티 저장
  │     └── 4. ⭐ concertRankingService.onSeatReserved(date)
  │           └── ZINCRBY ranking:soldout 1 "2025-12-25"
  │
사용자 → GET /concerts/ranking?topN=10
  │
  └── ConcertRankingService.getTopRanking(10)
        └── ZREVRANGE ranking:soldout 0 9 WITHSCORES
```

### 6. 동시성 안전성

- **ZINCRBY는 Redis의 원자적 연산**으로, 별도의 분산락 없이 동시성이 보장됨
- 100명이 동시에 예약해도 정확한 카운트가 유지됨
- 좌석 예약 자체의 동시성은 기존 분산락(@DistributedLock)으로 처리

---

## Redis 기반 대기열 설계

### 1. 기존 DB 기반의 문제점

| 문제 | 설명 |
|------|------|
| **폴링 부하** | 대기 순서 조회마다 `SELECT COUNT(*) + ORDER BY` 실행 |
| **락 경합** | 토큰 활성화 시 Bulk Update로 비관적 락 필요 |
| **불필요한 I/O** | 만료 토큰도 DB에 계속 보관 |
| **스케줄러 복잡도** | 대기 순서 재계산을 위한 전체 대기자 조회 + 개별 UPDATE |

### 2. Redis 기반 설계

#### 데이터 구조

```
┌────────────────────────────────────────────────────────┐
│  queue:waiting  (Sorted Set)                            │
│  WAITING 대기열 — member=token, score=timestamp          │
│  ┌──────────────────────────────────────┐               │
│  │  token-abc123  │  1703001200000      │               │
│  │  token-def456  │  1703001200100      │               │
│  │  token-ghi789  │  1703001200200      │               │
│  └──────────────────────────────────────┘               │
├────────────────────────────────────────────────────────┤
│  queue:active   (Set)                                   │
│  활성 토큰 집합                                          │
│  { "token-xyz111", "token-xyz222", ... }                │
├────────────────────────────────────────────────────────┤
│  queue:token:{token}  (String, TTL=600s)                │
│  토큰 → userId 매핑 (TTL로 자동 만료)                    │
│  "token-xyz111" → "user-123"                            │
├────────────────────────────────────────────────────────┤
│  queue:user:{userId}  (String)                          │
│  userId → token 매핑 (기존 토큰 확인용)                   │
│  "user-123" → "token-xyz111"                            │
└────────────────────────────────────────────────────────┘
```

#### 연산 복잡도 비교

| 연산 | DB 기반 | Redis 기반 |
|------|---------|-----------|
| 대기열 추가 | `INSERT` + `SELECT COUNT` = O(N) | `ZADD` + `ZRANK` = O(log N) |
| 대기 순서 조회 | `SELECT COUNT(*)` = O(N) | `ZRANK` = O(log N) |
| 활성 확인 | `SELECT ... WHERE token = ?` = O(log N) | `SISMEMBER` = O(1) |
| 대기→활성 전환 | `UPDATE + SELECT TOP N` = O(N) | `ZRANGE + ZREM + SADD` = O(log N) |
| 토큰 만료 | `UPDATE ... WHERE expires < NOW()` | TTL 자동 만료 = O(0) |

### 3. 핵심 흐름

#### 토큰 발급 흐름
```
POST /queue/token { userId: "user-123" }
  │
  ├── 1. 기존 토큰 확인
  │    GET queue:user:user-123 → "token-abc"
  │    ├── 활성? SISMEMBER queue:active token-abc → YES → 재사용
  │    └── 대기? ZRANK queue:waiting token-abc → 순서 반환
  │
  ├── 2. 새 토큰 생성 (UUID)
  │
  ├── 3. 활성 슬롯 확인
  │    SCARD queue:active < 100?
  │    ├── YES → 즉시 활성화
  │    │    SADD queue:active token-new
  │    │    SET queue:token:token-new "user-123" EX 600
  │    │    SET queue:user:user-123 token-new
  │    │
  │    └── NO → 대기열 추가
  │         ZADD queue:waiting {timestamp} token-new
  │         SET queue:token:token-new "user-123"
  │         SET queue:user:user-123 token-new
  │
  └── Response: { token, position, expiresIn }
```

#### 스케줄러 (대기 → 활성 전환)
```
매 30초마다:
  │
  ├── 1. SCARD queue:active → activeCount
  │
  ├── 2. toActivate = 100 - activeCount
  │
  ├── 3. ZRANGE queue:waiting 0 (toActivate-1) → tokens
  │
  └── 4. 각 token에 대해:
       ├── ZREM queue:waiting token
       ├── SADD queue:active token
       └── EXPIRE queue:token:token 600
```

#### TTL 기반 자동 만료
```
기존 DB 방식:
  스케줄러 → SELECT 만료 대상 → Bulk UPDATE → 매 5분

Redis 방식:
  SET queue:token:{token} "userId" EX 600  ← TTL 600초 설정
  → 10분 후 Redis가 자동으로 키 삭제
  → isActive() 호출 시 SISMEMBER + EXISTS 로 확인
  → 키 만료되면 SREM queue:active 로 정리

  ✅ 별도 만료 스케줄러 불필요!
```

### 4. 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│  Interface Layer                                         │
│  QueueController                                         │
│  POST /queue/token  |  GET /queue/status                 │
├─────────────────────────────────────────────────────────┤
│  Application Layer                                       │
│  QueueService (Redis 기반)                               │
│  - issueToken() → ZADD / SADD                           │
│  - validateToken() → SISMEMBER                          │
│  - activateWaitingTokens() → ZRANGE + SADD              │
├─────────────────────────────────────────────────────────┤
│  Domain Layer                                            │
│  RedisQueueRepository (Interface)                        │
│  - addToWaitingQueue() / activateToken()                │
│  - isActive() / isWaiting() / getWaitingPosition()      │
├─────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                    │
│  RedisQueueRepositoryImpl                                │
│  → StringRedisTemplate                                   │
│  RedisQueueScheduler (30초마다 대기→활성 전환)            │
└─────────────────────────────────────────────────────────┘
```

---

## 구현 회고

### 잘 된 점

1. **Clean Architecture 유지**: 도메인 인터페이스(ConcertRankingRepository, RedisQueueRepository)를 분리하여 Redis 구현 세부사항이 비즈니스 로직에 노출되지 않음

2. **ZINCRBY 원자적 연산 활용**: 랭킹 업데이트에 별도의 분산락이 필요 없어 성능과 코드 단순성 확보

3. **TTL 기반 자동 만료**: 기존 DB 방식의 만료 스케줄러 3개(예약 만료, 토큰 활성화, 토큰 만료)를 1개(예약 만료)로 줄이고, 토큰 만료는 Redis TTL에 위임

4. **기존 API 계약 유지**: QueueService 인터페이스를 변경하지 않아 Controller, ReservationService 등 상위 레이어 코드 변경 최소화

### 개선할 점

1. **Redis 장애 대응**: Redis 다운 시 대기열과 랭킹 모두 동작 불가. Redis Sentinel 또는 Cluster 구성 필요

2. **활성 집합 정리**: TTL로 토큰 키는 자동 만료되지만, `queue:active` Set에서의 제거는 `isActive()` 호출 시 lazy하게 처리. 주기적 정리 스크립트 추가 고려

3. **랭킹 기간 관리**: 현재 `ranking:soldout` 키에 모든 기간의 데이터가 누적됨. 일별/주별 랭킹이 필요하면 `ranking:soldout:2025-01-15` 같은 키 분리 필요

4. **Redis Lua Script**: 현재 여러 Redis 명령을 순차적으로 실행하는데, 원자성이 필요한 복합 연산은 Lua Script로 묶는 것이 더 안전

### 성능 기대 효과

| 지표 | DB 기반 | Redis 기반 | 개선 |
|------|---------|-----------|------|
| 대기 순서 조회 | ~5ms | <0.1ms | 50x |
| 토큰 검증 | ~3ms | <0.1ms | 30x |
| 랭킹 Top 10 | ~10ms | <0.1ms | 100x |
| 만료 처리 | Bulk Update 5분마다 | TTL 자동 | 스케줄러 제거 |
| DB 부하 | 매 요청 쿼리 | Redis 처리 | DB 부하 0 |

### 파일 변경 목록

#### 신규 파일 (랭킹)
- `ConcertRankingRepository.java` — 도메인 인터페이스
- `ConcertRankingRedisRepository.java` — Redis Sorted Set 구현체
- `ConcertRankingService.java` — 랭킹 서비스
- `ConcertRankingResponse.java` — 응답 DTO
- `ConcertRankingServiceTest.java` — 단위 테스트
- `ConcertRankingIntegrationTest.java` — 통합 테스트

#### 신규 파일 (대기열)
- `RedisQueueRepository.java` — 도메인 인터페이스
- `RedisQueueRepositoryImpl.java` — Redis 구현체
- `RedisQueueService.java` — Redis 대기열 서비스
- `RedisQueueScheduler.java` — 대기→활성 전환 스케줄러
- `RedisQueueIntegrationTest.java` — 통합 테스트

#### 수정 파일
- `ConcertController.java` — 랭킹 API 엔드포인트 추가
- `ReservationService.java` — 매진 랭킹 업데이트 연동
- `QueueService.java` — DB 기반 → Redis 기반 전환
- `ExpirationScheduler.java` — 대기열 스케줄러 제거 (Redis로 이관)
- `QueueServiceTest.java` — Redis 기반 단위 테스트로 전환
- `ReserveSeatUseCaseTest.java` — ConcertRankingService Mock 추가
