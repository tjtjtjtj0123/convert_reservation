# Point Module

포인트(Point) 도메인의 클린 아키텍처 구조입니다.

## 패키지 구조

```
point/
├── application/
│   └── service/
│       └── PointService.java                   # 포인트 관리 비즈니스 로직
├── domain/
│   ├── model/
│   │   └── PointBalance.java                   # 포인트 잔액 엔티티
│   └── repository/
│       └── PointBalanceRepository.java         # 포인트 리포지토리 인터페이스
├── infrastructure/
│   └── persistence/
│       ├── PointBalanceJpaRepository.java      # JPA Repository
│       └── PointBalanceRepositoryImpl.java     # Repository 구현체
└── interfaces/
    └── api/
        ├── PointController.java                # REST API 컨트롤러
        └── dto/
            ├── PointBalanceResponse.java       # 잔액 조회 응답 DTO
            ├── PointChargeRequest.java         # 충전 요청 DTO
            └── PointChargeResponse.java        # 충전 응답 DTO
```

## 레이어별 역할

### Domain Layer (`domain/`)
- **순수 비즈니스 로직**과 **도메인 모델**을 포함
- 외부 의존성 없음 (Framework, Infrastructure에 독립적)
- `PointBalance`: 포인트 잔액 엔티티
  - `charge(Long amount)`: 포인트 충전
  - `use(Long amount)`: 포인트 사용
  - `hasEnoughBalance(Long amount)`: 잔액 충분 여부 확인
- `PointBalanceRepository`: 리포지토리 인터페이스 (구현체는 Infrastructure에 위치)

### Application Layer (`application/`)
- **유스케이스**와 **비즈니스 서비스** 로직
- Domain Layer를 조합하여 비즈니스 요구사항 구현
- `PointService`: 포인트 관리 서비스
  - 포인트 충전
  - 포인트 잔액 조회
  - 포인트 사용 (내부 메서드)

### Infrastructure Layer (`infrastructure/`)
- **외부 시스템**과의 **실제 구현**
- Database, 외부 API 등과의 연동
- `PointBalanceJpaRepository`: Spring Data JPA 인터페이스
  - 비관적 락(`PESSIMISTIC_WRITE`) 지원
- `PointBalanceRepositoryImpl`: Domain의 `PointBalanceRepository` 구현체

### Interface Layer (`interfaces/`)
- **외부와의 상호작용** (API, CLI, Event 등)
- 요청/응답 변환
- `PointController`: REST API 엔드포인트
- `PointChargeRequest/Response`: 충전 관련 DTO
- `PointBalanceResponse`: 잔액 조회 응답 DTO

## 의존성 방향

```
Interfaces → Application → Domain ← Infrastructure
```

- **Domain**: 가장 안쪽, 의존성 없음
- **Application**: Domain에만 의존
- **Infrastructure**: Domain 인터페이스 구현
- **Interfaces**: Application 호출

## 주요 기능

### 포인트 충전
- **Endpoint**: `POST /points/charge`
- **인증**: 불필요
- **설명**: 사용자 포인트를 충전합니다
- **검증**:
  - 충전 금액은 0보다 커야 함
  - 사용자가 없는 경우 자동 생성

### 포인트 잔액 조회
- **Endpoint**: `GET /points/balance?userId={userId}`
- **인증**: 불필요
- **설명**: 사용자의 현재 포인트 잔액을 조회합니다
- **특징**:
  - 사용자가 없는 경우 잔액 0으로 반환

### 포인트 사용 (내부 메서드)
- **메서드**: `usePoint(String userId, Long amount)`
- **설명**: 결제 등에서 포인트를 차감합니다
- **동시성 제어**:
  - **비관적 락(Pessimistic Lock)** 사용
  - 금액 정합성 보장
- **검증**:
  - 잔액 부족 시 예외 발생

## 도메인 모델

### PointBalance
```java
- userId: String               // 사용자 ID (PK)
- balance: Long                 // 포인트 잔액
- version: Long                 // 낙관적 락 버전
```

### 비즈니스 규칙
1. **충전**: 양수 금액만 가능
2. **사용**: 양수 금액이며 잔액 이상 사용 불가
3. **동시성**: 비관적 락으로 동시 사용 제어

## 동시성 제어

### 비관적 락 (Pessimistic Lock)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM PointBalance p WHERE p.userId = :userId")
Optional<PointBalance> findByUserIdWithLock(@Param("userId") String userId);
```

- **사용 위치**: `usePoint()` 메서드
- **목적**: 결제 시 포인트 차감의 정합성 보장
- **동작**: 트랜잭션 중 다른 트랜잭션의 읽기/쓰기 차단

### 낙관적 락 (Optimistic Lock)
```java
@Version
private Long version;
```

- **목적**: 충전 등 일반적인 업데이트 시 충돌 감지
- **동작**: 버전 불일치 시 예외 발생

## 참고

이 구조는 **Clean Architecture** 원칙을 따릅니다:
- 도메인 중심 설계
- 의존성 역전 원칙 (DIP)
- 관심사의 분리
- 테스트 가능성
- 동시성 제어를 통한 데이터 정합성 보장
