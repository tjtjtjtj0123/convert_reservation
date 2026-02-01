# 예약/결제 시스템 - 클린 아키텍처 구현

## 📐 아키텍처 구조

예약/결제 시스템을 **클린 아키텍처(Clean Architecture)** 원칙을 따라 구현했습니다.

```
src/main/java/kr/hhplus/be/server/
├── domain/                          # 도메인 계층
│   ├── concert/
│   │   ├── Seat.java                # 좌석 도메인 엔티티
│   │   ├── SeatStatus.java          # 좌석 상태
│   │   └── SeatRepository.java      # Repository 인터페이스
│   ├── reservation/
│   │   ├── Reservation.java         # 예약 도메인 엔티티
│   │   ├── ReservationStatus.java   # 예약 상태
│   │   ├── ReservationRepository.java
│   │   ├── ReservationDomainException.java
│   │   └── ReservationErrorCode.java
│   └── payment/
│       ├── Payment.java             # 결제 도메인 엔티티
│       ├── PaymentStatus.java       # 결제 상태
│       ├── PaymentMethod.java       # 결제 수단
│       ├── PaymentRepository.java
│       ├── PaymentDomainException.java
│       └── PaymentErrorCode.java
│
├── application/                     # 애플리케이션 계층
│   ├── reservation/
│   │   ├── ReservationService.java  # 예약 유스케이스
│   │   └── ReservationResult.java   # DTO
│   └── payment/
│       ├── PaymentService.java      # 결제 유스케이스
│       ├── PaymentRequest.java      # DTO
│       └── PaymentResult.java       # DTO
│
└── interfaces/                      # 인터페이스 계층
    └── api/
        ├── reservation/
        │   ├── ReservationController.java
        │   └── dto/
        │       ├── SeatReserveRequest.java
        │       └── SeatReserveResponse.java
        └── payment/
            ├── PaymentController.java
            └── dto/
                ├── PaymentRequestDto.java
                └── PaymentResponseDto.java

src/test/java/kr/hhplus/be/server/
└── application/
    ├── reservation/
    │   └── ReservationServiceTest.java    # 예약 서비스 단위 테스트
    └── payment/
        └── PaymentServiceTest.java        # 결제 서비스 단위 테스트
```

## 🎯 핵심 비즈니스 로직

### 1. 좌석 예약 (Seat.java)

**임시 배정 (reserve)**
```java
public void reserve(Long userId) {
    // 예약 가능 여부 확인
    if (this.status != SeatStatus.AVAILABLE) {
        throw new IllegalStateException("예약 가능한 좌석이 아닙니다");
    }
    
    // 좌석 점유 (5분간)
    this.status = SeatStatus.TEMPORARILY_RESERVED;
    this.reservedByUserId = userId;
    this.reservedAt = LocalDateTime.now();
    this.reserveExpiresAt = LocalDateTime.now().plusMinutes(5);
}
```

**소유권 확정 (confirmReservation)**
```java
public void confirmReservation() {
    // 결제 완료 후 호출
    if (this.status != SeatStatus.TEMPORARILY_RESERVED) {
        throw new IllegalStateException("임시 예약 상태가 아닙니다");
    }
    
    this.status = SeatStatus.RESERVED;
    this.reserveExpiresAt = null;  // 만료 시간 제거
}
```

**좌석 해제 (release)**
```java
public void release() {
    // 임시 예약 만료 또는 취소 시
    this.status = SeatStatus.AVAILABLE;
    this.reservedByUserId = null;
    this.reservedAt = null;
    this.reserveExpiresAt = null;
}
```

### 2. 예약 관리 (Reservation.java)

**예약 생성**
```java
public static Reservation create(Long userId, Long seatId, 
                                 Long concertScheduleId, Long price) {
    Reservation reservation = new Reservation();
    reservation.status = ReservationStatus.PENDING;
    reservation.expiresAt = LocalDateTime.now().plusMinutes(5);
    return reservation;
}
```

**예약 확정**
```java
public void confirm() {
    if (this.status != ReservationStatus.PENDING) {
        throw new IllegalStateException("대기 중인 예약만 확정할 수 있습니다");
    }
    
    if (isExpired()) {
        throw new IllegalStateException("예약 시간이 만료되었습니다");
    }
    
    this.status = ReservationStatus.CONFIRMED;
    this.confirmedAt = LocalDateTime.now();
    this.expiresAt = null;
}
```

### 3. 결제 처리 (Payment.java)

**결제 완료**
```java
public void complete() {
    if (this.status != PaymentStatus.PENDING) {
        throw new IllegalStateException("대기 중인 결제만 완료할 수 있습니다");
    }
    
    this.status = PaymentStatus.COMPLETED;
    this.paidAt = LocalDateTime.now();
}
```

## 🔄 비즈니스 플로우

### 예약 플로우
```
1. 사용자 요청 (대기열 토큰 검증)
   ↓
2. 좌석 조회 (비관적 락)
   ↓
3. 예약 가능 여부 확인
   ↓
4. 좌석 임시 배정 (5분)
   seat.reserve(userId)
   ↓
5. 예약 레코드 생성
   Reservation.create()
```

### 결제 플로우
```
1. 결제 요청 (대기열 토큰 검증)
   ↓
2. 예약 조회 및 검증 (비관적 락)
   - 예약 존재 여부
   - 권한 확인
   - 만료 시간 확인
   - 금액 일치 여부
   ↓
3. 결제 처리
   payment.complete()
   ↓
4. 예약 확정
   reservation.confirm()
   ↓
5. 좌석 소유권 확정
   seat.confirmReservation()
   ↓
6. 대기열 토큰 만료
   queueTokenService.expireToken()
```

## 🧪 단위 테스트 전략

### Mock 처리 대상
- ✅ **SeatRepository** - Mock
- ✅ **ReservationRepository** - Mock
- ✅ **PaymentRepository** - Mock
- ✅ **QueueTokenService** - Mock

### 테스트 검증 항목

#### ReservationServiceTest
1. **좌석 예약 성공**
   - 좌석 상태가 `TEMPORARILY_RESERVED`로 변경
   - 예약 레코드 생성
   - 만료 시간 설정 (5분)

2. **좌석 예약 실패**
   - 존재하지 않는 좌석
   - 이미 예약된 좌석
   - 만료된 대기열 토큰

3. **예약 취소**
   - 예약 상태 변경
   - 좌석 해제
   - 권한 검증

#### PaymentServiceTest
1. **결제 성공**
   - 결제 완료
   - 예약 확정
   - 좌석 소유권 확정
   - 대기열 토큰 만료

2. **결제 실패**
   - 예약 없음
   - 예약 만료
   - 금액 불일치
   - 이미 확정된 예약

## 📋 API 명세

### 1. 좌석 예약
**Request:**
```http
POST /reservations
Headers: X-QUEUE-TOKEN: <대기열-토큰>

{
  "userId": 12345,
  "seatId": 1001
}
```

**Response:**
```json
{
  "reservationId": 101,
  "seatNumber": 15,
  "status": "PENDING",
  "price": 150000,
  "reservedAt": "2026-01-26T14:30:00",
  "expiresAt": "2026-01-26T14:35:00"
}
```

### 2. 결제
**Request:**
```http
POST /payment
Headers: X-QUEUE-TOKEN: <대기열-토큰>

{
  "userId": 12345,
  "reservationId": 101,
  "amount": 150000,
  "paymentMethod": "POINT"
}
```

**Response:**
```json
{
  "paymentId": 201,
  "reservationId": 101,
  "seatNumber": 15,
  "amount": 150000,
  "paymentStatus": "COMPLETED",
  "paymentMethod": "POINT",
  "paidAt": "2026-01-26T14:35:00",
  "reservationStatus": "CONFIRMED"
}
```

## 🔐 동시성 제어

### 비관적 락 적용
```java
// 좌석 조회 시
seatRepository.findByIdWithLock(seatId);

// 예약 조회 시
reservationRepository.findByIdWithLock(reservationId);
```

### 동시성 문제 해결
1. **동시 예약 방지**: 좌석 조회 시 비관적 락
2. **중복 결제 방지**: 예약 조회 시 비관적 락
3. **분산 환경 대응**: 데이터베이스 레벨 락 사용

## 📊 상태 전이도

### 좌석 상태
```
AVAILABLE → TEMPORARILY_RESERVED → RESERVED
    ↑              ↓
    └──────────────┘
      (5분 만료 또는 취소)
```

### 예약 상태
```
PENDING → CONFIRMED
   ↓
CANCELLED / EXPIRED
```

### 결제 상태
```
PENDING → COMPLETED
   ↓
FAILED / CANCELLED
```

## 🚀 테스트 실행

```bash
# 단위 테스트 실행
./gradlew test --tests "*ReservationServiceTest"
./gradlew test --tests "*PaymentServiceTest"

# 전체 테스트 실행
./gradlew test
```

## ✅ 클린 아키텍처 준수

### 의존성 규칙
- ✅ Domain은 외부에 의존하지 않음
- ✅ Application은 Domain에만 의존
- ✅ Infrastructure는 Domain 인터페이스 구현
- ✅ Interfaces는 Application 사용

### 도메인 중심 설계
- ✅ 비즈니스 로직이 도메인 엔티티에 캡슐화
- ✅ Repository 인터페이스를 도메인에 정의
- ✅ 도메인 예외로 비즈니스 규칙 위반 표현

### 테스트 용이성
- ✅ Mock을 사용한 독립적 단위 테스트
- ✅ 비즈니스 로직만 집중 검증
- ✅ 외부 의존성 격리
