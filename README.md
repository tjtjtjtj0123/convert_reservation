# 🎫 콘서트 예약 서비스

대기열 기반 콘서트 좌석 예약 시스템입니다.

## 📋 프로젝트 개요

트래픽 급증 상황에서도 안정적인 서비스를 제공하기 위해 **대기열 시스템**과 **동시성 제어**가 적용된 콘서트 좌석 예약 서비스입니다.

### 주요 기능
- 🎟️ **대기열 시스템** - 트래픽 제어 및 공정한 접근 보장
- 💺 **좌석 예약** - 5분 임시 배정 후 결제 완료 시 확정
- 💳 **포인트 결제** - 충전 및 차감 처리
- ⏰ **자동 만료 처리** - 스케줄러 기반 리소스 정리

### 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1 |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8.0 (운영) / H2 (개발) |
| Build Tool | Gradle (Kotlin DSL) |
| API Docs | OpenAPI 3.0 (Swagger UI) |
| Testing | JUnit 5, Mockito, Testcontainers |
| Infra | Docker & Docker Compose |

## 🏗️ 프로젝트 구조

```
src/main/java/kr/hhplus/be/server/
├── ServerApplication.java          # 메인 애플리케이션
│
├── application/                    # 애플리케이션 계층
│   ├── concert/
│   │   └── ConcertService.java         # 공연 조회 (레이어드)
│   ├── point/
│   │   └── PointService.java           # 포인트 관리 (레이어드)
│   ├── queue/
│   │   └── QueueService.java           # 대기열 관리 (레이어드)
│   ├── reservation/
│   │   └── usecase/
│   │       └── ReserveSeatUseCase.java     # 좌석 예약 (클린)
│   ├── payment/
│   │   └── usecase/
│   │       └── ProcessPaymentUseCase.java  # 결제 처리 (클린)
│   └── scheduler/
│       └── ExpirationScheduler.java    # 만료 처리 스케줄러
│
├── domain/                         # 도메인 계층
│   ├── concert/                        # 공연/좌석
│   ├── reservation/                    # 예약
│   ├── payment/                        # 결제
│   ├── point/                          # 포인트
│   └── queue/                          # 대기열
│
├── interfaces/                     # 인터페이스 계층
│   └── api/
│       ├── concert/                    # 공연 API
│       ├── reservation/                # 예약 API
│       ├── payment/                    # 결제 API
│       ├── point/                      # 포인트 API
│       └── queue/                      # 대기열 API
│
├── common/                         # 공통 모듈
│   └── exception/                      # 예외 처리
│
└── config/                         # 설정
    ├── swagger/                        # Swagger 설정
    └── jpa/                            # JPA 설정
```

## 🔧 아키텍처

### 하이브리드 아키텍처 적용

| 아키텍처 | 적용 도메인 | 이유 |
|----------|-------------|------|
| **Clean Architecture** | 예약, 결제 | 복잡한 비즈니스 로직, 트랜잭션 관리 |
| **Layered Architecture** | 공연, 포인트, 대기열 | 단순 CRUD, 빠른 개발 |

### 동시성 제어 전략

| 전략 | 적용 대상 | 목적 |
|------|----------|------|
| **낙관적 락** (\`@Version\`) | Seat, PointBalance | 충돌이 적은 상황에서 성능 확보 |
| **비관적 락** (\`PESSIMISTIC_WRITE\`) | 포인트 차감 | 금액 정합성 보장 |

📖 자세한 내용은 [Architecture 문서](docs/ARCHITECTURE.md)를 참고하세요.

## 📡 API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| \`POST\` | \`/api/queue/token\` | 대기열 토큰 발급 |
| \`GET\` | \`/api/concerts/{concertId}/dates\` | 예약 가능 날짜 조회 |
| \`GET\` | \`/api/concerts/{concertId}/dates/{date}/seats\` | 예약 가능 좌석 조회 |
| \`POST\` | \`/api/reservations\` | 좌석 예약 |
| \`POST\` | \`/api/payments\` | 결제 처리 |
| \`GET\` | \`/api/points/{userId}\` | 포인트 조회 |
| \`POST\` | \`/api/points/{userId}/charge\` | 포인트 충전 |

📖 상세 스펙은 [OpenAPI 문서](docs/openapi.yaml)를 참고하세요.

## 🗃️ 데이터베이스 스키마

| 테이블 | 설명 |
|--------|------|
| \`seat\` | 공연별 좌석 정보 |
| \`concert_schedule\` | 공연 일정 정보 |
| \`reservation\` | 예약 내역 |
| \`payment\` | 결제 내역 |
| \`point_balance\` | 사용자 포인트 잔액 |
| \`queue_token\` | 대기열 토큰 |

📖 DDL 스크립트는 [schema.sql](docs/schema.sql)을 참고하세요.

## 🚀 시작하기

### 사전 요구사항

- Java 21 이상
- Docker & Docker Compose
- Gradle 8.x

### 1. 저장소 클론

```bash
git clone https://github.com/your-repo/convert_reservation.git
cd convert_reservation
```

### 2. MySQL 컨테이너 실행

```bash
docker-compose up -d
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 4. API 문서 접속

```
http://localhost:8080/swagger-ui.html
```

## 🧪 테스트

### 전체 테스트 실행

```bash
./gradlew test
```

### 테스트 구성

| 테스트 파일 | 대상 |
|-------------|------|
| \`ConcertServiceTest\` | 공연 조회 서비스 |
| \`PointServiceTest\` | 포인트 관리 서비스 |
| \`QueueServiceTest\` | 대기열 관리 서비스 |
| \`ReserveSeatUseCaseTest\` | 좌석 예약 유스케이스 |
| \`ProcessPaymentUseCaseTest\` | 결제 처리 유스케이스 |

> ⚠️ **참고**: 통합 테스트 실행 시 Docker가 필요합니다.

## 📄 문서

| 문서 | 설명 |
|------|------|
| [Architecture](docs/ARCHITECTURE.md) | 시스템 아키텍처 상세 설명 |
| [Sequence Diagram](docs/SEQUENCE_DIAGRAM.md) | 주요 플로우 시퀀스 다이어그램 |
| [Schema](docs/schema.sql) | 데이터베이스 DDL 스크립트 |
| [OpenAPI](docs/openapi.yaml) | API 스펙 문서 |
| [ER Diagram](docs/ER-diagram.md) | 엔티티 관계 다이어그램 |

## 📦 빌드

```bash
# 빌드
./gradlew clean build

# JAR 파일 실행
java -jar build/libs/server-0.0.1-SNAPSHOT.jar
```

## 🛠️ 개발 환경 설정

### 프로파일

| 프로파일 | 용도 | 데이터베이스 |
|----------|------|--------------|
| \`default\` | 개발 | H2 (인메모리) |
| \`dev\` | 로컬 개발 | MySQL (Docker) |

```bash
# dev 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 🐳 Docker 관리

### 컨테이너 시작
```bash
docker-compose up -d
```

### 컨테이너 중지
```bash
docker-compose down
```

### 컨테이너 및 데이터 삭제
```bash
docker-compose down -v
```

### 로그 확인
```bash
docker-compose logs -f mysql
```

## 📝 라이센스

이 프로젝트는 MIT 라이센스를 따릅니다.
