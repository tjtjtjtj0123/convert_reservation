# Queue 모듈 - 도메인 기반 클린 아키텍처

## 📋 목차
- [개요](#개요)
- [아키텍처 구조](#아키텍처-구조)
- [계층별 설명](#계층별-설명)
- [주요 기능](#주요-기능)
- [의존성 방향](#의존성-방향)
- [사용 예시](#사용-예시)

## 개요

Queue 모듈은 콘서트 예약 시스템의 대기열 관리 기능을 담당하며, 도메인 기반 클린 아키텍처 원칙을 따라 설계되었습니다.

### 핵심 책임
- 대기열 토큰 발급 및 관리
- 토큰 상태 추적 (WAITING, ACTIVE, EXPIRED)
- 활성 토큰 수 제한을 통한 부하 제어
- 토큰 만료 및 자동 활성화

## 아키텍처 구조

```
queue/
├── domain/                          # 도메인 계층 (핵심 비즈니스 로직)
│   ├── model/
│   │   ├── QueueToken.java         # 대기열 토큰 엔티티
│   │   └── TokenStatus.java        # 토큰 상태 Enum
│   └── repository/
│       └── QueueTokenRepository.java # 리포지토리 인터페이스
│
├── application/                     # 애플리케이션 계층 (유스케이스)
│   └── service/
│       └── QueueService.java       # 대기열 서비스
│
├── infrastructure/                  # 인프라 계층 (기술 구현)
│   └── persistence/
│       ├── QueueTokenJpaRepository.java    # JPA 리포지토리
│       └── QueueTokenRepositoryImpl.java   # 리포지토리 구현체
│
└── interfaces/                      # 인터페이스 계층 (외부 연동)
    └── api/
        ├── QueueController.java    # REST API 컨트롤러
        └── dto/
            ├── QueueTokenRequest.java   # 토큰 발급 요청 DTO
            └── QueueTokenResponse.java  # 토큰 응답 DTO
```

## 계층별 설명

### 1. Domain Layer (도메인 계층)

#### QueueToken (대기열 토큰 엔티티)
```java
@Entity
@Table(name = "queue_token")
public class QueueToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String userId;
    
    @Column(nullable = false, unique = true)
    private String token;
    
    @Column(nullable = false)
    private Integer position;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus status;
    
    // 비즈니스 메서드
    public void activate(LocalDateTime expiresAt);
    public void expire();
    public boolean isExpired();
    public boolean isActive();
}
```

**핵심 비즈니스 규칙**:
- 토큰은 고유한 UUID로 식별됩니다
- 각 사용자는 하나의 유효한 토큰만 가질 수 있습니다
- 토큰 상태 전환: WAITING → ACTIVE → EXPIRED
- 활성화된 토큰은 10분간 유효합니다

#### TokenStatus (토큰 상태)
```java
public enum TokenStatus {
    WAITING,   // 대기 중
    ACTIVE,    // 활성화됨
    EXPIRED    // 만료됨
}
```

#### QueueTokenRepository (리포지토리 인터페이스)
도메인 계층에 위치한 인터페이스로, 인프라 계층에서 구현합니다.

```java
public interface QueueTokenRepository {
    QueueToken save(QueueToken token);
    Optional<QueueToken> findById(Long id);
    Optional<QueueToken> findByToken(String token);
    Optional<QueueToken> findByUserId(String userId);
    List<QueueToken> findAllByStatus(TokenStatus status);
    
    // 대기열 관리
    int countByStatus(TokenStatus status);
    List<QueueToken> findWaitingTokensInOrder(int limit);
    List<QueueToken> findExpiredTokens(LocalDateTime now);
    
    // 배치 작업
    void updateStatus(List<Long> tokenIds, TokenStatus status);
    void deleteExpiredTokens(LocalDateTime expiryDate);
}
```

### 2. Application Layer (애플리케이션 계층)

#### QueueService
비즈니스 유스케이스를 조율하는 서비스입니다.

```java
@Service
@Transactional(readOnly = true)
public class QueueService {
    private static final int MAX_ACTIVE_TOKENS = 100;
    private static final int TOKEN_ACTIVE_MINUTES = 10;
    
    // 토큰 발급
    @Transactional
    public QueueTokenResponse issueToken(QueueTokenRequest request);
    
    // 토큰 검증
    public void validateToken(String token);
    
    // 토큰 상태 조회
    public QueueTokenResponse getTokenStatus(String token);
    
    // 토큰 만료 처리 (스케줄러)
    @Transactional
    public void expireTokens();
    
    // 대기 토큰 활성화 (스케줄러)
    @Transactional
    public void activateWaitingTokens();
}
```

**주요 로직**:
- `issueToken()`: 기존 토큰 확인 → 새 토큰 생성 → 자동 활성화 여부 결정
- `validateToken()`: 토큰 존재 여부 → 활성 상태 확인 → 만료 시간 검증
- `expireTokens()`: 만료된 토큰 조회 → 상태 EXPIRED로 변경
- `activateWaitingTokens()`: 활성 토큰 수 확인 → 대기 중인 토큰 활성화

### 3. Infrastructure Layer (인프라 계층)

#### QueueTokenJpaRepository
Spring Data JPA를 사용한 데이터 접근 인터페이스입니다.

```java
public interface QueueTokenJpaRepository extends JpaRepository<QueueToken, Long> {
    Optional<QueueToken> findByToken(String token);
    Optional<QueueToken> findByUserId(String userId);
    List<QueueToken> findAllByStatus(TokenStatus status);
    int countByStatus(TokenStatus status);
    
    @Query("SELECT qt FROM QueueToken qt WHERE qt.status = 'WAITING' ORDER BY qt.createdAt ASC")
    List<QueueToken> findWaitingTokensInOrder(Pageable pageable);
}
```

#### QueueTokenRepositoryImpl
도메인 리포지토리 인터페이스를 구현한 클래스입니다.

```java
@Repository
public class QueueTokenRepositoryImpl implements QueueTokenRepository {
    private final QueueTokenJpaRepository jpaRepository;
    
    // 도메인 인터페이스 메서드를 JPA 호출로 변환
    @Override
    public QueueToken save(QueueToken token) {
        return jpaRepository.save(token);
    }
    
    @Override
    public Optional<QueueToken> findByToken(String token) {
        return jpaRepository.findByToken(token);
    }
    
    // ... 기타 메서드
}
```

### 4. Interface Layer (인터페이스 계층)

#### QueueController
REST API 엔드포인트를 제공합니다.

```java
@RestController
@RequestMapping("/queue")
public class QueueController {
    private final QueueService queueService;
    
    // POST /queue/token - 토큰 발급
    @PostMapping("/token")
    public ResponseEntity<QueueTokenResponse> issueToken(@RequestBody QueueTokenRequest request);
    
    // GET /queue/status - 대기열 상태 조회
    @GetMapping("/status")
    public ResponseEntity<QueueTokenResponse> getStatus(@RequestHeader("X-QUEUE-TOKEN") String token);
}
```

#### DTOs
```java
// 토큰 발급 요청
public class QueueTokenRequest {
    private String userId;
}

// 토큰 응답
public class QueueTokenResponse {
    private String token;        // 대기열 토큰
    private Integer position;    // 현재 대기 순서
    private Integer expiresIn;   // 만료까지 남은 시간 (초)
}
```

## 주요 기능

### 1. 토큰 발급 흐름
```
1. 사용자 요청 (userId)
2. 기존 토큰 확인
   - 있으면: 기존 토큰 정보 반환
   - 없으면: 새 토큰 생성
3. 대기 순서 계산
4. 활성 토큰 수 확인
   - 여유 있으면: 즉시 ACTIVE 상태로 발급
   - 초과하면: WAITING 상태로 발급
5. 토큰 정보 반환 (token, position, expiresIn)
```

### 2. 토큰 검증 흐름
```
1. 헤더에서 토큰 추출 (X-QUEUE-TOKEN)
2. 토큰 존재 여부 확인
3. 토큰 상태 확인
   - ACTIVE: 검증 통과
   - WAITING: 대기 중 예외 발생
   - EXPIRED: 만료 예외 발생
4. 만료 시간 확인
   - 만료되지 않음: 검증 통과
   - 만료됨: 토큰 만료 처리 후 예외 발생
```

### 3. 스케줄러 작업
```java
// 1분마다 만료된 토큰 정리
@Scheduled(fixedRate = 60000)
public void scheduleExpireTokens() {
    queueService.expireTokens();
}

// 1분마다 대기 중인 토큰 활성화
@Scheduled(fixedRate = 60000)
public void scheduleActivateTokens() {
    queueService.activateWaitingTokens();
}
```

## 의존성 방향

```
Interface Layer  →  Application Layer  →  Domain Layer
                                              ↑
Infrastructure Layer  ─────────────────────┘
```

### 핵심 원칙
1. **Domain Layer**: 외부 의존성 없음 (순수 비즈니스 로직)
2. **Application Layer**: Domain에만 의존
3. **Infrastructure Layer**: Domain 인터페이스 구현 (의존성 역전)
4. **Interface Layer**: Application과 Domain 사용

## 사용 예시

### 1. 토큰 발급
```java
// Request
POST /queue/token
{
  "userId": "user-123"
}

// Response (활성화된 경우)
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "position": 0,
  "expiresIn": 600
}

// Response (대기 중인 경우)
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "position": 42,
  "expiresIn": null
}
```

### 2. 대기열 상태 조회
```java
// Request
GET /queue/status
Headers:
  X-QUEUE-TOKEN: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

// Response
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "position": 5,
  "expiresIn": 450
}
```

### 3. 다른 서비스에서 토큰 검증
```java
@Service
public class PaymentService {
    private final QueueService queueService;
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String token) {
        // 대기열 토큰 검증
        queueService.validateToken(token);
        
        // 결제 처리
        // ...
    }
}
```

## 마이그레이션 노트

### 변경 사항
기존 레이어드 아키텍처에서 도메인 기반 클린 아키텍처로 전환:

```
Before (Layered Architecture):
- application/queue/QueueService.java
- domain/queue/QueueToken.java
- domain/queue/QueueTokenRepository.java
- interfaces/api/queue/QueueController.java

After (Clean Architecture):
- queue/domain/model/QueueToken.java
- queue/domain/repository/QueueTokenRepository.java
- queue/application/service/QueueService.java
- queue/infrastructure/persistence/QueueTokenJpaRepository.java
- queue/infrastructure/persistence/QueueTokenRepositoryImpl.java
- queue/interfaces/api/QueueController.java
- queue/interfaces/api/dto/QueueTokenRequest.java
- queue/interfaces/api/dto/QueueTokenResponse.java
```

### 하위 호환성
기존 패키지는 `@Deprecated` 어노테이션과 함께 유지되며, 점진적 마이그레이션을 지원합니다:
- `kr.hhplus.be.server.application.queue.QueueService` → `kr.hhplus.be.server.queue.application.service.QueueService`
- `kr.hhplus.be.server.interfaces.api.queue.*` → `kr.hhplus.be.server.queue.interfaces.api.*`

### 업데이트된 파일
다음 파일들의 import가 새 패키지로 업데이트되었습니다:
- `ProcessPaymentUseCaseImpl.java`
- `PaymentService.java`
- `ProcessPaymentUseCaseTest.java`
- `ReserveSeatUseCaseImpl.java`
- `ReserveSeatUseCaseTest.java`
- `ConcertController.java`

---

**작성일**: 2025-01-XX  
**버전**: 1.0.0  
**작성자**: Clean Architecture Migration Team
