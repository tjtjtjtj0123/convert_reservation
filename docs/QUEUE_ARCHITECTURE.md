# 대기열 시스템 - 클린 아키텍처 구현

## 📐 아키텍처 구조

이 프로젝트는 **클린 아키텍처(Clean Architecture)** 원칙을 따라 대기열 시스템을 구현했습니다.

```
src/main/java/kr/hhplus/be/server/
├── domain/                    # 도메인 계층 (비즈니스 규칙)
│   └── queue/
│       ├── QueueToken.java              # 도메인 엔티티
│       ├── QueueStatus.java             # 상태 Enum
│       ├── QueuePolicy.java             # 비즈니스 정책
│       ├── QueueTokenRepository.java    # 인터페이스 (DIP)
│       ├── QueueDomainException.java    # 도메인 예외
│       └── QueueErrorCode.java          # 에러 코드
│
├── application/               # 애플리케이션 계층 (유스케이스)
│   └── queue/
│       ├── QueueTokenService.java       # 토큰 발급/검증 서비스
│       ├── QueueScheduler.java          # 대기열 자동화 스케줄러
│       └── QueuePositionInfo.java       # DTO
│
├── infrastructure/            # 인프라스트럭처 계층 (외부 기술)
│   └── queue/
│       ├── QueueTokenEntity.java                    # JPA 엔티티
│       ├── QueueTokenJpaRepositoryWithLock.java     # Spring Data JPA
│       ├── QueueTokenRepositoryWithLockImpl.java    # Repository 구현체
│       └── QueueTokenMapper.java                    # Domain ↔ Entity 매퍼
│
└── interfaces/                # 인터페이스 계층 (외부 어댑터)
    └── api/
        └── queue/
            ├── QueueController.java     # REST API 컨트롤러
            └── dto/
                ├── QueueTokenRequest.java
                └── QueueTokenResponse.java
```

### 계층별 의존성 방향
```
Interfaces → Application → Domain ← Infrastructure
```
- **Domain**: 의존성 없음 (순수 비즈니스 로직)
- **Application**: Domain에만 의존
- **Infrastructure**: Domain 인터페이스 구현
- **Interfaces**: Application 사용

## 🎯 핵심 기능

### 1. 대기열 토큰 발급
- **POST** `/queue/token`
- 사용자를 대기열에 등록하고 토큰 발급
- 이미 토큰이 있으면 기존 토큰 반환

### 2. 대기열 상태 조회
- **GET** `/queue/status`
- 헤더: `X-QUEUE-TOKEN`
- 현재 대기 순서 및 예상 대기 시간 조회

### 3. 대기열 고도화 기능

#### 자동 토큰 활성화 (스케줄러)
- **실행 주기**: 1분마다
- **정책**:
  - 최대 활성 유저: 50명
  - 1회 활성화: 10명씩
  - 대기 순서대로 자동 승격

#### 만료 토큰 자동 정리
- **실행 주기**: 1분마다
- 활성 토큰 유효기간: 10분
- 만료된 토큰 자동 EXPIRED 처리

## 🔐 동시성 제어

### 비관적 락 (Pessimistic Lock)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

다수의 인스턴스가 동시에 실행되더라도 안전하게 동작하도록:
- 토큰 발급 시 사용자별 중복 방지
- 활성화 시 동일 토큰 중복 처리 방지
- 대기 순서 재정렬 시 일관성 보장

## 📊 비즈니스 정책 (`QueuePolicy`)

| 항목 | 값 | 설명 |
|------|-----|------|
| MAX_ACTIVE_USERS | 50 | 동시 활성 유저 수 제한 |
| ACTIVE_TOKEN_EXPIRY_MINUTES | 10 | 활성 토큰 유효 기간 (분) |
| TOKENS_TO_ACTIVATE_PER_BATCH | 10 | 1회 활성화 처리 토큰 수 |
| SCHEDULER_INTERVAL_SECONDS | 60 | 스케줄러 실행 주기 (초) |

## 🗄️ 데이터베이스

### queue_token 테이블
```sql
CREATE TABLE queue_token (
    token_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(100) UNIQUE NOT NULL,
    position INT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    
    INDEX idx_token (token),
    INDEX idx_user_status (user_id, status),
    INDEX idx_status_position (status, position)
);
```

### 인덱스 전략
- `idx_token`: 토큰 조회 최적화
- `idx_user_status`: 사용자별 토큰 조회
- `idx_status_position`: 대기열 순서 조회 및 활성화

## 🔄 대기열 처리 흐름

### 1. 토큰 발급
```
사용자 요청 → 기존 토큰 확인 → 없으면 새 토큰 생성 → 대기 순서 배정
```

### 2. 자동 활성화
```
스케줄러 실행 (1분) → 만료 토큰 정리 → 활성 슬롯 확인 → 대기 토큰 활성화 → 순서 재정렬
```

### 3. 토큰 검증
```
API 요청 → 헤더에서 토큰 추출 → 유효성 검증 → 활성 상태 확인 → 통과/거부
```

## 📈 예상 대기 시간 계산

```java
estimatedWaitMinutes = Math.ceil(remainingCount / 10.0)
```
- 1분당 10명씩 활성화
- 남은 대기 인원 ÷ 10 = 예상 대기 시간(분)

## 🧪 테스트 전략

### 단위 테스트
- Domain 계층: 비즈니스 로직 검증
- Application 계층: 유스케이스 시나리오

### 통합 테스트
- API 엔드포인트 테스트
- 동시성 시나리오 테스트

### 성능 테스트
- 다수 사용자 동시 토큰 발급
- 스케줄러 부하 테스트

## 🚀 실행 방법

### 1. 데이터베이스 테이블 생성
```sql
-- docs/queue-ddl.sql 실행
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3. API 테스트
```bash
# 토큰 발급
curl -X POST http://localhost:8080/queue/token \
  -H "Content-Type: application/json" \
  -d '{"userId": 12345}'

# 대기열 상태 조회
curl -X GET http://localhost:8080/queue/status \
  -H "X-QUEUE-TOKEN: <발급받은-토큰>"
```

## 📝 API 명세

### 1. 토큰 발급
**Request:**
```json
POST /queue/token
{
  "userId": 12345
}
```

**Response:**
```json
{
  "token": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "WAITING",
  "position": 15,
  "remainingCount": 14,
  "estimatedWaitMinutes": 2
}
```

### 2. 대기열 상태 조회
**Request:**
```
GET /queue/status
Headers: X-QUEUE-TOKEN: f47ac10b-58cc-4372-a567-0e02b2c3d479
```

**Response (대기 중):**
```json
{
  "token": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "WAITING",
  "position": 10,
  "remainingCount": 9,
  "estimatedWaitMinutes": 1
}
```

**Response (활성):**
```json
{
  "token": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "ACTIVE",
  "expiresAt": "2026-01-26T15:30:00",
  "expiresInSeconds": 600
}
```

## 🔧 설정

### application.yml
```yaml
spring:
  jpa:
    properties:
      hibernate:
        # 2차 캐시 비활성화 (동시성 이슈 방지)
        cache.use_second_level_cache: false
```

## 💡 클린 아키텍처 원칙 준수

### 1. 의존성 역전 원칙 (DIP)
- `QueueTokenRepository` 인터페이스를 Domain에 정의
- Infrastructure 계층이 이를 구현

### 2. 단일 책임 원칙 (SRP)
- Domain: 비즈니스 규칙
- Application: 유스케이스 조율
- Infrastructure: 기술 구현
- Interfaces: 외부 통신

### 3. 개방-폐쇄 원칙 (OCP)
- 새로운 기능 추가 시 기존 코드 수정 최소화
- Repository 교체 가능 (JPA → Redis 등)

## 🎨 주요 디자인 패턴

- **Repository Pattern**: 데이터 접근 추상화
- **Factory Pattern**: `QueueToken.createWaitingToken()`
- **Mapper Pattern**: Domain ↔ Entity 변환
- **Strategy Pattern**: `QueuePolicy` 정책 분리
