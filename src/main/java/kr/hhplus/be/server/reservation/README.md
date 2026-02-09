# Reservation 모듈 - 도메인 기반 클린 아키텍처

## 📋 목차
- [개요](#개요)
- [아키텍처 구조](#아키텍처-구조)
- [계층별 설명](#계층별-설명)
- [주요 기능](#주요-기능)
- [의존성 방향](#의존성-방향)
- [사용 예시](#사용-예시)

## 개요

Reservation 모듈은 콘서트 좌석 예약 기능을 담당하며, 도메인 기반 클린 아키텍처 원칙을 따라 설계되었습니다.

### 핵심 책임
- 좌석 임시 예약 (5분간 유효)
- 예약 상태 관리 (TEMP_HELD, CONFIRMED, CANCELLED, EXPIRED)
- 예약 확정 및 취소
- 만료된 예약 처리

## 아키텍처 구조

```
reservation/
├── domain/                              # 도메인 계층 (핵심 비즈니스 로직)
│   ├── model/
│   │   ├── Reservation.java            # 예약 엔티티
│   │   └── ReservationStatus.java      # 예약 상태 Enum
│   └── repository/
│       └── ReservationRepository.java  # 리포지토리 인터페이스
│
├── application/                         # 애플리케이션 계층 (유스케이스)
│   └── service/
│       └── ReservationService.java     # 예약 서비스
│
├── infrastructure/                      # 인프라 계층 (기술 구현)
│   └── persistence/
│       ├── ReservationJpaRepository.java    # JPA 리포지토리
│       └── ReservationRepositoryImpl.java   # 리포지토리 구현체
│
└── interfaces/                          # 인터페이스 계층 (외부 연동)
    └── api/
        ├── ReservationController.java  # REST API 컨트롤러
        └── dto/
            ├── SeatReserveRequest.java      # 예약 요청 DTO
            └── SeatReserveResponse.java     # 예약 응답 DTO
```

## 계층별 설명

### 1. Domain Layer (도메인 계층)

#### Reservation (예약 엔티티)
```java
@Entity
@Table(name = "reservation", indexes = {
    @Index(name = "idx_reservation_status", columnList = "status"),
    @Index(name = "idx_reservation_status_until", columnList = "status, reserved_until"),
    @Index(name = "idx_reservation_user_id", columnList = "user_id"),
    @Index(name = "idx_reservation_seat_id", columnList = "seat_id"),
    @Index(name = "idx_reservation_user_date_seat", columnList = "user_id, concert_date, seat_number, status")
})
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private Long seatId;
    
    @Column(nullable = false)
    private String concertDate;
    
    @Column(nullable = false)
    private Integer seatNumber;
    
    @Column(nullable = false)
    private Long price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;
    
    private LocalDateTime reservedAt;
    private LocalDateTime reservedUntil;
    private LocalDateTime confirmedAt;
    
    // 비즈니스 메서드
    public static Reservation create(String userId, Long seatId, ...);
    public void confirm();
    public void cancel();
    public void expire();
    public boolean isExpired();
}
```

**핵심 비즈니스 규칙**:
- 임시 예약은 5분간 유효합니다
- TEMP_HELD → CONFIRMED (결제 완료 시)
- TEMP_HELD → EXPIRED (5분 경과 시)
- TEMP_HELD → CANCELLED (사용자 취소 시)
- 만료된 예약은 자동으로 EXPIRED 상태로 전환됩니다

#### ReservationStatus (예약 상태)
```java
public enum ReservationStatus {
    TEMP_HELD,    // 임시 배정 (5분간 유효)
    CONFIRMED,    // 예약 확정 (결제 완료)
    CANCELLED,    // 예약 취소
    EXPIRED       // 예약 만료
}
```

#### ReservationRepository (리포지토리 인터페이스)
도메인 계층에 위치한 인터페이스로, 인프라 계층에서 구현합니다.

```java
public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(Long id);
    Optional<Reservation> findByUserIdAndSeatIdAndStatus(String userId, Long seatId, ReservationStatus status);
    Optional<Reservation> findByUserIdAndConcertDateAndSeatNumberAndStatus(...);
    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    List<Long> findSeatIdsByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    int bulkExpire(LocalDateTime now);
}
```

### 2. Application Layer (애플리케이션 계층)

#### ReservationService
비즈니스 유스케이스를 조율하는 서비스입니다.

```java
@Service
@Transactional(readOnly = true)
public class ReservationService {
    private static final Long MOCK_PRICE = 150000L;
    
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final QueueService queueService;
    
    // 좌석 예약
    @Transactional
    public SeatReserveResponse reserveSeat(SeatReserveRequest request, String queueToken);
}
```

**주요 로직**:
- `reserveSeat()`: 토큰 검증 → 좌석 조회 및 락 획득 → 좌석 예약 가능 여부 확인 → 좌석 예약 → 예약 엔티티 생성 → 응답 반환

### 3. Infrastructure Layer (인프라 계층)

#### ReservationJpaRepository
Spring Data JPA를 사용한 데이터 접근 인터페이스입니다.

```java
interface ReservationJpaRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByUserIdAndSeatIdAndStatus(String userId, Long seatId, ReservationStatus status);
    
    Optional<Reservation> findByUserIdAndConcertDateAndSeatNumberAndStatus(...);
    
    List<Reservation> findByStatusAndReservedUntilBefore(ReservationStatus status, LocalDateTime time);
    
    @Query("SELECT r.seatId FROM Reservation r WHERE r.status = :status AND r.reservedUntil < :time")
    List<Long> findSeatIdsByStatusAndReservedUntilBefore(@Param("status") ReservationStatus status, ...);
    
    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'EXPIRED' WHERE r.status = 'TEMP_HELD' AND r.reservedUntil < :now")
    int bulkExpire(@Param("now") LocalDateTime now);
}
```

#### ReservationRepositoryImpl
도메인 리포지토리 인터페이스를 구현한 클래스입니다.

```java
@Repository
@Transactional(readOnly = true)
public class ReservationRepositoryImpl implements ReservationRepository {
    private final ReservationJpaRepository jpaRepository;
    
    @Override
    @Transactional
    public Reservation save(Reservation reservation) {
        return jpaRepository.save(reservation);
    }
    
    // ... 기타 메서드
}
```

### 4. Interface Layer (인터페이스 계층)

#### ReservationController
REST API 엔드포인트를 제공합니다.

```java
@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService reservationService;
    
    // POST /reservations - 좌석 임시 예약
    @PostMapping
    public ResponseEntity<SeatReserveResponse> reserveSeat(
        @RequestHeader("X-QUEUE-TOKEN") String token,
        @RequestBody SeatReserveRequest request
    );
}
```

#### DTOs
```java
// 좌석 예약 요청
public class SeatReserveRequest {
    private String userId;
    private String date;
    private Integer seatNumber;
}

// 좌석 예약 응답
public class SeatReserveResponse {
    private Integer seatNumber;
    private LocalDateTime tempHoldExpires;
    private ReservationStatus status;
    
    public enum ReservationStatus {
        TEMP_HELD,
        RESERVED
    }
}
```

## 주요 기능

### 1. 좌석 예약 흐름
```
1. 사용자 요청 (userId, date, seatNumber)
2. 대기열 토큰 검증 (QueueService)
3. 좌석 조회 및 비관적 락 획득 (SeatRepository)
4. 좌석 예약 가능 여부 확인
   - 만료된 임시 예약은 자동 해제
5. 좌석 예약 처리 (Seat.reserve())
6. 예약 엔티티 생성 및 저장 (Reservation.create())
7. 응답 반환 (좌석 번호, 만료 시간, 상태)
```

### 2. 예약 확정 흐름 (결제 시)
```
1. 예약 ID로 예약 조회
2. 예약 상태 검증 (TEMP_HELD인지 확인)
3. 예약 만료 여부 검증
4. 예약 확정 (Reservation.confirm())
   - status: TEMP_HELD → CONFIRMED
   - confirmedAt: 현재 시간으로 설정
5. 좌석 상태 업데이트 (Seat.confirm())
```

### 3. 만료 예약 처리 (스케줄러)
```java
// 1분마다 만료된 예약 처리
@Scheduled(fixedRate = 60000)
public void expireReservations() {
    // Bulk Update로 한 번에 처리
    int count = reservationRepository.bulkExpire(LocalDateTime.now());
    
    // 만료된 예약의 좌석 ID 조회
    List<Long> seatIds = reservationRepository
        .findSeatIdsByStatusAndReservedUntilBefore(
            ReservationStatus.TEMP_HELD, 
            LocalDateTime.now()
        );
    
    // 좌석 상태 복원 (RESERVED → AVAILABLE)
    seatRepository.bulkRelease(seatIds);
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

### 1. 좌석 예약
```java
// Request
POST /reservations
Headers:
  X-QUEUE-TOKEN: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Body:
{
  "userId": "user-123",
  "date": "2025-12-25",
  "seatNumber": 42
}

// Response
{
  "seatNumber": 42,
  "tempHoldExpires": "2025-12-25T14:35:00",
  "status": "TEMP_HELD"
}
```

### 2. 결제 서비스에서 예약 확정
```java
@Service
public class PaymentService {
    private final ReservationRepository reservationRepository;
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String token) {
        // 예약 조회
        Reservation reservation = reservationRepository
            .findById(request.getReservationId())
            .orElseThrow(() -> new BusinessException("예약을 찾을 수 없습니다."));
        
        // 예약 확정 (도메인 로직)
        reservation.confirm();
        
        // 결제 처리
        // ...
    }
}
```

### 3. 스케줄러에서 만료 처리
```java
@Component
public class ReservationScheduler {
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 만료된 예약들을 한 번에 EXPIRED 처리
        int expiredCount = reservationRepository.bulkExpire(now);
        
        // 2. 만료된 예약의 좌석 ID 조회
        List<Long> seatIds = reservationRepository
            .findSeatIdsByStatusAndReservedUntilBefore(
                ReservationStatus.TEMP_HELD, 
                now
            );
        
        // 3. 좌석 상태 복원
        if (!seatIds.isEmpty()) {
            seatRepository.bulkRelease(seatIds);
        }
        
        log.info("만료된 예약 {} 건 처리 완료", expiredCount);
    }
}
```

## 데이터베이스 인덱스 설계

### 인덱스 목록
```sql
-- 1. 예약 상태별 조회 (일반 조회)
CREATE INDEX idx_reservation_status ON reservation(status);

-- 2. 만료 예약 조회 최적화 (스케줄러)
CREATE INDEX idx_reservation_status_until ON reservation(status, reserved_until);

-- 3. 사용자별 예약 조회
CREATE INDEX idx_reservation_user_id ON reservation(user_id);

-- 4. 좌석별 예약 조회
CREATE INDEX idx_reservation_seat_id ON reservation(seat_id);

-- 5. 사용자의 특정 날짜/좌석 예약 조회 (중복 예약 방지)
CREATE INDEX idx_reservation_user_date_seat ON reservation(user_id, concert_date, seat_number, status);
```

### 인덱스 활용 쿼리
```sql
-- 만료된 예약 조회 (idx_reservation_status_until 사용)
SELECT * FROM reservation 
WHERE status = 'TEMP_HELD' AND reserved_until < NOW();

-- 사용자의 특정 좌석 예약 조회 (idx_reservation_user_date_seat 사용)
SELECT * FROM reservation 
WHERE user_id = 'user-123' 
  AND concert_date = '2025-12-25' 
  AND seat_number = 42 
  AND status = 'TEMP_HELD';
```

## 마이그레이션 노트

### 변경 사항
기존 레이어드 아키텍처에서 도메인 기반 클린 아키텍처로 전환:

```
Before (Layered Architecture):
- domain/reservation/Reservation.java
- domain/reservation/ReservationStatus.java
- domain/reservation/ReservationRepository.java
- infrastructure/persistence/reservation/ReservationJpaRepository.java
- infrastructure/persistence/reservation/ReservationRepositoryImpl.java
- application/reservation/usecase/ReserveSeatUseCase.java
- application/reservation/usecase/ReserveSeatUseCaseImpl.java
- interfaces/api/reservation/ReservationController.java
- interfaces/api/reservation/dto/SeatReserveRequest.java
- interfaces/api/reservation/dto/SeatReserveResponse.java

After (Clean Architecture):
- reservation/domain/model/Reservation.java
- reservation/domain/model/ReservationStatus.java
- reservation/domain/repository/ReservationRepository.java
- reservation/infrastructure/persistence/ReservationJpaRepository.java
- reservation/infrastructure/persistence/ReservationRepositoryImpl.java
- reservation/application/service/ReservationService.java
- reservation/interfaces/api/ReservationController.java
- reservation/interfaces/api/dto/SeatReserveRequest.java
- reservation/interfaces/api/dto/SeatReserveResponse.java
```

### 하위 호환성
기존 패키지는 `@Deprecated` 어노테이션과 함께 유지되며, 점진적 마이그레이션을 지원합니다:
- `kr.hhplus.be.server.domain.reservation.*` → `kr.hhplus.be.server.reservation.domain.model.*`
- `kr.hhplus.be.server.application.reservation.*` → `kr.hhplus.be.server.reservation.application.service.*`
- `kr.hhplus.be.server.interfaces.api.reservation.*` → `kr.hhplus.be.server.reservation.interfaces.api.*`

### 업데이트된 파일
다음 파일들의 import가 새 패키지로 업데이트되었습니다:
- `ProcessPaymentUseCaseImpl.java`
- `PaymentService.java`
- `ProcessPaymentUseCaseTest.java`
- `ReserveSeatUseCaseTest.java`

---

**작성일**: 2026-02-03  
**버전**: 1.0.0  
**작성자**: Clean Architecture Migration Team
