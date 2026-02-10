# Concert 도메인 - 클린 아키텍처

## 📁 디렉토리 구조

```
concert/
├── domain/                          # 🎯 Domain Layer (핵심 비즈니스 로직)
│   ├── model/                      # 도메인 모델
│   │   ├── Seat.java              # 좌석 엔티티
│   │   ├── SeatStatus.java        # 좌석 상태 열거형
│   │   └── ConcertSchedule.java   # 공연 일정 엔티티
│   └── repository/                 # 리포지토리 인터페이스
│       ├── SeatRepository.java
│       └── ConcertScheduleRepository.java
│
├── application/                     # 🔧 Application Layer (Use Cases)
│   └── service/
│       └── ConcertService.java     # 공연 조회 유스케이스
│
├── infrastructure/                  # 🏗️ Infrastructure Layer (구현체)
│   └── persistence/                # 영속성 구현
│       ├── SeatJpaRepository.java
│       ├── SeatRepositoryImpl.java
│       ├── ConcertScheduleJpaRepository.java
│       └── ConcertScheduleRepositoryImpl.java
│
└── interfaces/                      # 🌐 Interface Layer (외부 통신)
    └── api/                        # REST API
        ├── ConcertController.java  # 공연 조회 컨트롤러
        └── dto/                    # DTO
            ├── AvailableDatesResponse.java
            ├── SeatListResponse.java
            └── SeatStatus.java
```

## 🏛️ 아키텍처 레이어

### 1. Domain Layer (도메인 계층)
**위치**: `concert/domain/`

핵심 비즈니스 로직과 규칙이 위치합니다. 다른 계층에 의존하지 않습니다.

#### Model
- **Seat**: 좌석 엔티티
  - 좌석 예약, 확정, 해제 등의 비즈니스 로직 포함
  - 낙관적 락(@Version)을 통한 동시성 제어
  
- **SeatStatus**: 좌석 상태 (AVAILABLE, TEMP_HELD, RESERVED)
  
- **ConcertSchedule**: 공연 일정
  - 예약 가능 좌석 수 관리

#### Repository Interface
- **SeatRepository**: 좌석 데이터 접근 인터페이스
- **ConcertScheduleRepository**: 공연 일정 데이터 접근 인터페이스

### 2. Application Layer (응용 계층)
**위치**: `concert/application/service/`

도메인 객체들을 조합하여 유스케이스를 구현합니다.

- **ConcertService**: 
  - 예약 가능한 날짜 조회
  - 좌석 목록 조회
  - 좌석 초기화 (Mock 데이터)

### 3. Infrastructure Layer (인프라 계층)
**위치**: `concert/infrastructure/persistence/`

도메인 인터페이스의 구현체를 제공합니다.

- **SeatJpaRepository**: Spring Data JPA 인터페이스
- **SeatRepositoryImpl**: SeatRepository 구현체
- **ConcertScheduleJpaRepository**: Spring Data JPA 인터페이스
- **ConcertScheduleRepositoryImpl**: ConcertScheduleRepository 구현체

### 4. Interface Layer (인터페이스 계층)
**위치**: `concert/interfaces/api/`

외부 세계와의 통신을 담당합니다.

- **ConcertController**: REST API 엔드포인트
  - `GET /concerts/available-dates`: 예약 가능 날짜 조회
  - `GET /concerts/seats?date={date}`: 좌석 목록 조회

## 🔄 의존성 방향

```
Interface Layer (Controller)
    ↓
Application Layer (Service/Use Case)
    ↓
Domain Layer (Model, Repository Interface)
    ↑
Infrastructure Layer (Repository Impl)
```

## ✨ 주요 특징

### 1. 의존성 역전 원칙 (DIP)
- Domain Layer는 Infrastructure에 의존하지 않음
- Repository는 Interface로 정의되고, Infrastructure에서 구현

### 2. 단일 책임 원칙 (SRP)
- 각 계층은 명확한 책임을 가짐
- Domain은 비즈니스 로직만 담당
- Infrastructure는 기술적 구현만 담당

### 3. 테스트 용이성
- Domain Layer는 순수 Java로 작성되어 단위 테스트 용이
- Mock을 통한 의존성 주입으로 각 계층 독립 테스트 가능

## 📝 마이그레이션 노트

기존 레이어드 아키텍처에서 도메인 기반 클린 아키텍처로 전환:

### Before (레이어드)
```
server/
├── domain/concert/          # 엔티티 + 리포지토리 인터페이스
├── application/concert/     # 서비스
├── infrastructure/concert/  # 리포지토리 구현
└── interfaces/concert/      # 컨트롤러
```

### After (도메인 기반 클린)
```
server/
└── concert/                 # 도메인별 패키징
    ├── domain/             # 핵심 비즈니스
    ├── application/        # 유스케이스
    ├── infrastructure/     # 기술 구현
    └── interfaces/         # 외부 인터페이스
```

## 🔧 다음 단계

다른 도메인들도 순차적으로 마이그레이션 예정:
- [ ] Queue 도메인
- [ ] Reservation 도메인
- [ ] Payment 도메인
- [ ] Point 도메인
