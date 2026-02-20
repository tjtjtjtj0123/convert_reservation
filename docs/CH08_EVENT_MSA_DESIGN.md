# CH08 이벤트 기반 트랜잭션 분리 & MSA 설계 문서

## 📋 목차
1. [개요](#1-개요)
2. [이벤트 기반 아키텍처 적용](#2-이벤트-기반-아키텍처-적용)
3. [MSA 도메인 분리 설계](#3-msa-도메인-분리-설계)
4. [트랜잭션 처리의 한계와 해결방안](#4-트랜잭션-처리의-한계와-해결방안)
5. [SAGA 패턴 적용 설계](#5-saga-패턴-적용-설계)

---

## 1. 개요

### 1.1 배경
콘서트 예약 시스템의 결제 완료 후 **데이터 플랫폼 전송**이라는 부가 로직이 핵심 결제 트랜잭션에 강결합되어 있었습니다.
이를 Spring Application Event를 활용하여 **트랜잭션과 관심사를 분리**하고, MSA 전환을 위한 설계를 수립합니다.

### 1.2 요구사항
- **[필수]** 예약 정보를 데이터 플랫폼에 전송(Mock API 호출)하는 요구사항을 이벤트로 분리
- **[선택]** MSA 도메인 분리 설계 및 트랜잭션 처리 한계/해결방안 문서화

---

## 2. 이벤트 기반 아키텍처 적용

### 2.1 Before: 강결합 구조

```
PaymentService.processPayment()
├── 1. 토큰 검증
├── 2. 예약 조회
├── 3. 만료 확인
├── 4. 포인트 차감         ← 핵심 로직
├── 5. 좌석 상태 변경       ← 핵심 로직
├── 6. 예약 확정            ← 핵심 로직
├── 7. 결제 내역 생성       ← 핵심 로직
├── 8. 토큰 만료            ← 부가 로직 (결합)
└── (데이터 플랫폼 전송 없음)

ReservationService.reserveSeat()
├── 1~5. 좌석 예약 로직     ← 핵심 로직
├── 6. 매진 랭킹 업데이트   ← 부가 로직 (직접 호출, 강결합)
└── 7. 응답 생성
```

**문제점:**
- 데이터 플랫폼 전송 실패 시 결제 트랜잭션까지 롤백
- 랭킹 업데이트(Redis)가 DB 트랜잭션에 포함되어 장애 전파
- 부가 로직의 지연이 핵심 로직의 응답 시간에 영향

### 2.2 After: 이벤트 기반 분리 구조

```
PaymentService.processPayment()
├── 1~7. 핵심 결제 로직 (단일 트랜잭션)
├── 8. PaymentSuccessEvent 발행 ◀── 이벤트 발행
├── 9. 토큰 만료
└── 10. 응답 생성

  ┌──── @TransactionalEventListener(AFTER_COMMIT) + @Async ────┐
  │  PaymentEventListener                                       │
  │  └── DataPlatformSendService.sendPaymentData() (Mock API)   │
  └─────────────────────────────────────────────────────────────┘

ReservationService.reserveSeat()
├── 1~5. 핵심 예약 로직 (단일 트랜잭션)
├── 6. ReservationCompletedEvent 발행 ◀── 이벤트 발행
└── 7. 응답 생성

  ┌──── @TransactionalEventListener(AFTER_COMMIT) + @Async ────┐
  │  ReservationEventListener                                    │
  │  ├── ConcertRankingService.onSeatReserved() (랭킹 업데이트)  │
  │  └── DataPlatformSendService.sendReservationData() (Mock)    │
  └─────────────────────────────────────────────────────────────┘
```

### 2.3 핵심 설계 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| 이벤트 발행 시점 | `AFTER_COMMIT` | 트랜잭션 커밋 후에만 이벤트 처리하여 데이터 정합성 보장 |
| 비동기 처리 | `@Async` | 부가 로직의 지연/실패가 핵심 응답에 영향 없음 |
| 실패 처리 | try-catch + 로깅 | 부가 로직 실패가 핵심 트랜잭션에 영향 주지 않음 |
| 퍼블리셔 래핑 | `PaymentEventPublisher` | ApplicationEventPublisher 직접 의존 방지, 테스트 용이 |

### 2.4 생성된 파일 구조

```
payment/
├── application/
│   ├── event/
│   │   ├── PaymentEventPublisher.java      # 이벤트 발행 래퍼
│   │   └── PaymentEventListener.java       # @Async + @TransactionalEventListener
│   └── service/
│       └── PaymentService.java             # (수정) 이벤트 발행 추가
├── domain/
│   └── event/
│       └── PaymentSuccessEvent.java        # 결제 성공 이벤트 객체
└── infrastructure/
    └── external/
        └── DataPlatformSendService.java    # Mock 데이터 플랫폼 API

reservation/
├── application/
│   ├── event/
│   │   ├── ReservationEventPublisher.java  # 이벤트 발행 래퍼
│   │   └── ReservationEventListener.java   # @Async + @TransactionalEventListener
│   └── service/
│       └── ReservationService.java         # (수정) 직접 호출 → 이벤트 발행
└── domain/
    └── event/
        └── ReservationCompletedEvent.java  # 예약 완료 이벤트 객체
```

---

## 3. MSA 도메인 분리 설계

### 3.1 현재 모놀리식 도메인 구조

```
┌────────────────────────────────────────────────┐
│                  Monolith                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │  Concert  │ │   Queue  │ │   Reservation    │ │
│  │  Domain   │ │  Domain  │ │     Domain       │ │
│  └──────────┘ └──────────┘ └──────────────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │  Payment  │ │  Point   │ │    Ranking       │ │
│  │  Domain   │ │  Domain  │ │    Domain        │ │
│  └──────────┘ └──────────┘ └──────────────────┘ │
│           단일 MySQL + 단일 Redis                  │
└────────────────────────────────────────────────┘
```

### 3.2 MSA 배포 단위 설계

```
┌─────────────────┐    ┌────────────────┐    ┌────────────────────┐
│  Concert Service │    │  Queue Service  │    │ Reservation Service │
│  ─────────────── │    │  ────────────── │    │ ──────────────────  │
│  - 콘서트 조회    │    │  - 대기열 관리   │    │ - 좌석 예약         │
│  - 좌석 조회      │    │  - 토큰 발급     │    │ - 예약 관리         │
│  - 랭킹 조회      │    │  - 토큰 검증     │    │ - 좌석 상태 관리     │
│                  │    │                │    │                     │
│  [MySQL + Redis] │    │  [Redis Only]  │    │ [MySQL]             │
└─────────────────┘    └────────────────┘    └────────────────────┘
         │                     │                       │
    ─────┴─────────────────────┴───────────────────────┴─────
                        Message Broker (Kafka)
    ─────┬─────────────────────┬───────────────────────┬─────
         │                     │                       │
┌─────────────────┐    ┌────────────────┐    ┌────────────────────┐
│ Payment Service  │    │  Point Service │    │ DataPlatform Service│
│ ─────────────── │    │  ────────────── │    │ ──────────────────  │
│ - 결제 처리       │    │  - 포인트 충전  │    │ - 예약 데이터 수집   │
│ - 결제 내역       │    │  - 포인트 차감  │    │ - 결제 데이터 수집   │
│ - 결제 취소       │    │  - 잔액 조회    │    │ - 외부 API 연동      │
│                  │    │                │    │                     │
│ [MySQL]          │    │ [MySQL + Redis]│    │ [별도 DB]           │
└─────────────────┘    └────────────────┘    └────────────────────┘
```

### 3.3 도메인 분리 기준

| 서비스 | 핵심 책임 | 데이터 소유 | 분리 이유 |
|--------|----------|------------|----------|
| **Concert Service** | 콘서트/좌석 조회, 랭킹 | concert, seat, ranking | 읽기 중심, 캐싱 최적화 가능 |
| **Queue Service** | 대기열 토큰 관리 | Redis 대기열 | 독립적인 확장/축소, Redis 전용 |
| **Reservation Service** | 예약 생성/관리 | reservation | 예약 비즈니스의 핵심 Aggregate |
| **Payment Service** | 결제 처리 | payment | 결제 규제 준수, 독립 배포 |
| **Point Service** | 포인트 잔액 관리 | point | 금전적 리소스, 강한 정합성 필요 |
| **DataPlatform Service** | 외부 데이터 전송 | 전송 이력 | 외부 시스템 장애 격리 |

---

## 4. 트랜잭션 처리의 한계와 해결방안

### 4.1 현재 모놀리식 트랜잭션

```java
// 현재: 단일 @Transactional로 모든 로직이 하나의 트랜잭션
@Transactional
public PaymentResponse processPayment(PaymentRequest request) {
    pointService.usePoint(userId, amount);       // Point 도메인
    seat.confirm();                               // Concert 도메인
    reservation.confirm();                        // Reservation 도메인
    paymentRepository.save(payment);             // Payment 도메인
    // → 하나라도 실패하면 전체 롤백 (ACID 보장)
}
```

### 4.2 MSA 전환 시 트랜잭션 한계

| 한계 | 설명 |
|------|------|
| **분산 트랜잭션 불가** | 각 서비스가 독립 DB를 가지므로 @Transactional 불가 |
| **2PC 한계** | Two-Phase Commit은 성능 병목, 가용성 저하 |
| **부분 실패** | 포인트는 차감됐지만 결제 기록 저장 실패 가능 |
| **보상 로직 필요** | 실패 시 이미 완료된 작업을 되돌리는 로직 필요 |
| **최종 일관성** | 강한 일관성(Strong Consistency) → 최종 일관성(Eventual Consistency) 전환 필요 |

### 4.3 해결방안: Choreography SAGA 패턴

MSA에서는 **SAGA 패턴**으로 분산 트랜잭션을 관리합니다.

#### Choreography 방식 (이벤트 기반)

```
[결제 요청]
    │
    ▼
┌──────────────┐   PointDeductedEvent    ┌──────────────┐
│ Point Service │ ─────────────────────▶ │  Reservation  │
│  포인트 차감   │                        │   Service     │
│              │   PointDeductFailedEvent │  예약 확정     │
│              │ ◀───────────────────── │              │
└──────────────┘                        └──────────────┘
                                              │
                                   ReservationConfirmedEvent
                                              │
                                              ▼
                                   ┌──────────────┐
                                   │   Payment     │
                                   │   Service     │
                                   │   결제 기록    │
                                   └──────────────┘
                                              │
                                    PaymentSuccessEvent
                                              │
                              ┌───────────────┼───────────────┐
                              ▼               ▼               ▼
                       ┌──────────┐   ┌──────────┐   ┌──────────┐
                       │  Queue    │   │  Concert │   │  Data    │
                       │  Service  │   │  Service │   │ Platform │
                       │ 토큰 만료  │   │ 랭킹 업데이트│   │ 데이터 전송│
                       └──────────┘   └──────────┘   └──────────┘
```

#### 보상 트랜잭션 (Compensation)

```
결제 실패 시 보상 흐름:

PaymentFailedEvent
    │
    ├──▶ Reservation Service: 예약 취소 (reservation.cancel())
    ├──▶ Concert Service: 좌석 해제 (seat.release())
    └──▶ Point Service: 포인트 환불 (point.refund())
```

---

## 5. SAGA 패턴 적용 설계

### 5.1 결제 SAGA 시나리오

```
SAGA: 결제 처리

Step 1: Point Service - 포인트 차감
  성공 → Step 2
  실패 → 종료 (보상 불필요)

Step 2: Reservation Service - 예약 확정
  성공 → Step 3
  실패 → Compensate Step 1 (포인트 환불)

Step 3: Payment Service - 결제 기록 생성
  성공 → Step 4 (비동기)
  실패 → Compensate Step 2 (예약 취소) → Compensate Step 1 (포인트 환불)

Step 4: 비동기 부가 작업 (실패 허용)
  - Queue Service: 토큰 만료
  - Concert Service: 랭킹 업데이트
  - DataPlatform Service: 데이터 전송
```

### 5.2 Outbox 패턴 (이벤트 유실 방지)

MSA에서 이벤트 유실을 방지하기 위해 **Outbox 패턴**을 함께 적용합니다.

```
┌──────────────────────────────────┐
│          Payment Service          │
│  ┌─────────┐    ┌─────────────┐  │
│  │ Payment │    │   Outbox    │  │
│  │  Table  │    │   Table     │  │
│  │         │    │ (이벤트 저장) │  │
│  └─────────┘    └─────────────┘  │
│       └── 같은 DB 트랜잭션 ──┘     │
└──────────────────────────────────┘
         │
    Outbox Poller (CDC / Polling)
         │
         ▼
   ┌──────────┐
   │  Kafka   │ → Consumer Services
   └──────────┘
```

**동작 방식:**
1. 결제 저장과 Outbox 이벤트를 **같은 로컬 트랜잭션**으로 저장
2. 별도 Poller가 Outbox 테이블에서 미전송 이벤트를 읽어 Kafka 발행
3. 이벤트 유실 없이 최종 일관성 보장

### 5.3 현재 구현과 MSA 전환 전략

| 단계 | 현재 | MSA 전환 |
|------|------|----------|
| **이벤트 발행** | `ApplicationEventPublisher` | Kafka Producer |
| **이벤트 수신** | `@TransactionalEventListener` | Kafka Consumer |
| **비동기 처리** | `@Async` | Kafka Consumer 자체가 비동기 |
| **실패 처리** | try-catch + 로깅 | DLQ(Dead Letter Queue) + 재시도 |
| **이벤트 유실 방지** | Spring 내부 보장 | Outbox 패턴 |
| **보상 트랜잭션** | 불필요 (단일 DB) | SAGA Compensate |

현재 `ApplicationEvent` 기반 구현은 MSA 전환 시 **Kafka 기반으로 자연스럽게 교체**할 수 있도록 설계되어 있습니다.

---

## 📝 결론

1. **이벤트 기반 분리**: 핵심 로직(결제/예약)과 부가 로직(데이터 플랫폼, 랭킹)을 `@TransactionalEventListener(AFTER_COMMIT) + @Async`로 분리하여 장애 격리와 응답 성능 개선
2. **MSA 도메인 분리**: Concert, Queue, Reservation, Payment, Point, DataPlatform 6개 서비스로 분리
3. **트랜잭션 해결**: Choreography SAGA 패턴 + Outbox 패턴으로 분산 트랜잭션의 최종 일관성 보장
4. **점진적 전환**: 현재 Spring Event 구현 → Kafka 기반 MSA로 자연스러운 마이그레이션 경로 확보
