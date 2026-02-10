# Payment Module

결제(Payment) 도메인의 클린 아키텍처 구조입니다.

## 패키지 구조

```
payment/
├── application/
│   └── service/
│       └── PaymentService.java                # 결제 처리 비즈니스 로직
├── domain/
│   ├── model/
│   │   ├── Payment.java                        # 결제 엔티티
│   │   └── PaymentStatus.java                  # 결제 상태 (COMPLETED, CANCELLED, FAILED)
│   └── repository/
│       └── PaymentRepository.java              # 결제 리포지토리 인터페이스
├── infrastructure/
│   └── persistence/
│       ├── PaymentJpaRepository.java           # JPA Repository
│       └── PaymentRepositoryImpl.java          # Repository 구현체
└── interfaces/
    └── api/
        ├── PaymentController.java              # REST API 컨트롤러
        └── dto/
            ├── PaymentRequest.java             # 결제 요청 DTO
            └── PaymentResponse.java            # 결제 응답 DTO
```

## 레이어별 역할

### Domain Layer (`domain/`)
- **순수 비즈니스 로직**과 **도메인 모델**을 포함
- 외부 의존성 없음 (Framework, Infrastructure에 독립적)
- `Payment`: 결제 엔티티 (비즈니스 규칙 포함)
- `PaymentStatus`: 결제 상태 열거형
- `PaymentRepository`: 리포지토리 인터페이스 (구현체는 Infrastructure에 위치)

### Application Layer (`application/`)
- **유스케이스**와 **비즈니스 서비스** 로직
- Domain Layer를 조합하여 비즈니스 요구사항 구현
- `PaymentService`: 결제 처리 유스케이스
  - 토큰 검증
  - 예약 조회 및 검증
  - 포인트 차감
  - 좌석 상태 변경
  - 예약 확정
  - 결제 내역 생성

### Infrastructure Layer (`infrastructure/`)
- **외부 시스템**과의 **실제 구현**
- Database, 외부 API 등과의 연동
- `PaymentJpaRepository`: Spring Data JPA 인터페이스
- `PaymentRepositoryImpl`: Domain의 `PaymentRepository` 구현체

### Interface Layer (`interfaces/`)
- **외부와의 상호작용** (API, CLI, Event 등)
- 요청/응답 변환
- `PaymentController`: REST API 엔드포인트
- `PaymentRequest/Response`: API 요청/응답 DTO

## 의존성 방향

```
Interfaces → Application → Domain ← Infrastructure
```

- **Domain**: 가장 안쪽, 의존성 없음
- **Application**: Domain에만 의존
- **Infrastructure**: Domain 인터페이스 구현
- **Interfaces**: Application 호출

## 주요 기능

### 결제 처리
- **Endpoint**: `POST /payment`
- **설명**: 임시 예약된 좌석에 대한 결제 처리
- **흐름**:
  1. 대기열 토큰 검증
  2. 임시 예약 조회 및 만료 확인
  3. 포인트 차감
  4. 좌석 상태 변경 (TEMP_HELD → CONFIRMED)
  5. 예약 확정 (TEMP_HELD → CONFIRMED)
  6. 결제 내역 생성
  7. 토큰 만료 처리

## 도메인 모델

### Payment
```java
- id: Long                      // 결제 ID
- reservationId: Long           // 예약 ID
- userId: String                // 사용자 ID
- amount: Long                  // 결제 금액
- status: PaymentStatus         // 결제 상태
- paidAt: LocalDateTime         // 결제 시각
```

### PaymentStatus
- `COMPLETED`: 결제 완료
- `CANCELLED`: 결제 취소
- `FAILED`: 결제 실패

## 참고

이 구조는 **Clean Architecture** 원칙을 따릅니다:
- 도메인 중심 설계
- 의존성 역전 원칙 (DIP)
- 관심사의 분리
- 테스트 가능성
