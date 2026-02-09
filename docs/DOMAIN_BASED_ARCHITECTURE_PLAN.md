# 도메인 기반 클린 아키텍처 재구성 계획

## 🎯 목표

기능별 패키지 구조에서 **도메인 중심 패키지 구조**로 전환하여, 각 도메인이 독립적인 경계를 가지도록 재구성합니다.

## 📊 현재 구조 (Feature-based)

```
server/
├── application/          # 기능별 분리
│   ├── concert/
│   ├── reservation/
│   ├── payment/
│   ├── queue/
│   └── point/
├── domain/              # 기능별 분리
│   ├── concert/
│   ├── reservation/
│   ├── payment/
│   ├── queue/
│   └── point/
├── infrastructure/      # 기능별 분리
│   └── persistence/
│       ├── concert/
│       ├── reservation/
│       ├── payment/
│       ├── queue/
│       └── point/
└── interfaces/
    └── api/
```

## 🎯 목표 구조 (Domain-based Clean Architecture)

```
server/
├── concert/                    # 🎵 콘서트 도메인 (Bounded Context)
│   ├── domain/                # Domain Layer
│   │   ├── model/            # 도메인 모델 (Entity, VO)
│   │   │   ├── Seat.java
│   │   │   ├── SeatStatus.java
│   │   │   ├── ConcertSchedule.java
│   │   │   └── Concert.java
│   │   ├── repository/       # Repository Interface
│   │   │   ├── SeatRepository.java
│   │   │   └── ConcertScheduleRepository.java
│   │   └── service/          # Domain Service (선택적)
│   │       └── SeatDomainService.java
│   ├── application/           # Application Layer
│   │   ├── usecase/          # Use Cases
│   │   │   ├── GetAvailableSeatsUseCase.java
│   │   │   └── GetConcertSchedulesUseCase.java
│   │   └── service/          # Application Service
│   │       └── ConcertService.java
│   ├── infrastructure/        # Infrastructure Layer
│   │   └── persistence/
│   │       ├── SeatJpaRepository.java
│   │       ├── SeatRepositoryImpl.java
│   │       ├── ConcertScheduleJpaRepository.java
│   │       └── ConcertScheduleRepositoryImpl.java
│   └── interfaces/            # Interface Layer
│       ├── api/
│       │   ├── ConcertController.java
│       │   └── dto/
│       │       ├── SeatResponse.java
│       │       └── ScheduleResponse.java
│       └── event/            # 도메인 이벤트 리스너
│
├── reservation/                # 📝 예약 도메인 (Bounded Context)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Reservation.java
│   │   │   └── ReservationStatus.java
│   │   └── repository/
│   │       └── ReservationRepository.java
│   ├── application/
│   │   ├── usecase/
│   │   │   └── ReserveSeatUseCase.java
│   │   └── service/
│   │       └── ReservationService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── ReservationJpaRepository.java
│   │       └── ReservationRepositoryImpl.java
│   └── interfaces/
│       └── api/
│           ├── ReservationController.java
│           └── dto/
│
├── payment/                    # 💳 결제 도메인 (Bounded Context)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Payment.java
│   │   │   └── PaymentStatus.java
│   │   └── repository/
│   │       └── PaymentRepository.java
│   ├── application/
│   │   ├── usecase/
│   │   │   └── ProcessPaymentUseCase.java
│   │   └── service/
│   │       └── PaymentService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── PaymentJpaRepository.java
│   │       └── PaymentRepositoryImpl.java
│   └── interfaces/
│       └── api/
│           ├── PaymentController.java
│           └── dto/
│
├── queue/                      # 🔄 대기열 도메인 (Bounded Context)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── QueueToken.java
│   │   │   └── TokenStatus.java
│   │   └── repository/
│   │       └── QueueTokenRepository.java
│   ├── application/
│   │   ├── usecase/
│   │   └── service/
│   │       └── QueueService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── QueueTokenJpaRepository.java
│   │       └── QueueTokenRepositoryImpl.java
│   └── interfaces/
│       └── api/
│           ├── QueueController.java
│           └── dto/
│
├── point/                      # 💰 포인트 도메인 (Bounded Context)
│   ├── domain/
│   │   ├── model/
│   │   │   └── PointBalance.java
│   │   └── repository/
│   │       └── PointBalanceRepository.java
│   ├── application/
│   │   ├── usecase/
│   │   └── service/
│   │       └── PointService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── PointBalanceJpaRepository.java
│   │       └── PointBalanceRepositoryImpl.java
│   └── interfaces/
│       └── api/
│           ├── PointController.java
│           └── dto/
│
├── shared/                     # 🔗 공유 영역
│   ├── common/
│   │   ├── exception/
│   │   │   ├── BusinessException.java
│   │   │   └── ErrorCode.java
│   │   └── util/
│   ├── infrastructure/
│   │   ├── config/
│   │   │   ├── JpaConfig.java
│   │   │   └── SwaggerConfig.java
│   │   └── scheduler/
│   │       └── ExpirationScheduler.java
│   └── interfaces/
│       └── filter/
│
└── ServerApplication.java
```

## ✨ 주요 개선사항

### 1. 도메인 경계 명확화 (Bounded Context)
- 각 도메인이 완전히 독립적인 패키지 구조
- 도메인 간 의존성은 인터페이스를 통해서만

### 2. 계층별 명확한 분리
각 도메인 내에서:
- **Domain Layer**: 비즈니스 로직과 규칙 (model, repository interface, domain service)
- **Application Layer**: 유스케이스 조율 (usecase, application service)
- **Infrastructure Layer**: 기술 구현 (persistence, external API)
- **Interface Layer**: 외부 통신 (api, event)

### 3. 의존성 방향
```
Interfaces → Application → Domain
                ↓
         Infrastructure
```

### 4. 도메인 간 통신
- 직접 참조 금지
- 이벤트 기반 통신 또는 Application Service를 통한 조율

## 📝 마이그레이션 단계

### Phase 1: 도메인 구조 생성
1. 각 도메인별 디렉토리 구조 생성
2. domain/model, domain/repository 생성

### Phase 2: 파일 이동
1. Entity → domain/model
2. Repository Interface → domain/repository
3. Service → application/service
4. UseCase → application/usecase
5. Repository Impl → infrastructure/persistence
6. Controller → interfaces/api

### Phase 3: Import 경로 수정
1. 모든 import 문 업데이트
2. 테스트 코드 import 업데이트

### Phase 4: 검증
1. 빌드 테스트
2. 단위 테스트 실행
3. 통합 테스트 실행

## 🎯 장점

1. **높은 응집도**: 관련된 코드가 한 곳에 모임
2. **낮은 결합도**: 도메인 간 의존성 최소화
3. **확장성**: 새로운 도메인 추가 용이
4. **유지보수성**: 도메인별로 독립적인 변경 가능
5. **팀 협업**: 도메인별로 팀 분담 가능
6. **마이크로서비스 전환 용이**: 각 도메인이 독립적이므로 분리 쉬움

## 📚 참고

- Domain-Driven Design (DDD) - Eric Evans
- Clean Architecture - Robert C. Martin
- Hexagonal Architecture (Ports & Adapters) - Alistair Cockburn
