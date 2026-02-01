# 콘서트 예약 서비스 - 시퀀스 다이어그램

## 📋 목차
1. [전체 예약 플로우](#1-전체-예약-플로우)
2. [대기열 토큰 발급](#2-대기열-토큰-발급)
3. [좌석 예약](#3-좌석-예약-임시-배정)
4. [결제 처리](#4-결제-처리)
5. [만료 처리 스케줄러](#5-만료-처리-스케줄러)

---

## 1. 전체 예약 플로우

```mermaid
sequenceDiagram
    autonumber
    participant U as 사용자
    participant Q as 대기열 API
    participant C as 콘서트 API
    participant R as 예약 API
    participant P as 결제 API

    rect rgb(240, 248, 255)
        Note over U,Q: Phase 1: 대기열 진입
        U->>Q: POST /queue/token
        Q-->>U: 토큰 + 대기 순서 반환
        
        loop 대기열 확인 (폴링)
            U->>Q: GET /queue/status
            Q-->>U: 현재 순서 반환
        end
    end

    rect rgb(255, 250, 240)
        Note over U,C: Phase 2: 좌석 조회
        U->>C: GET /concerts/available-dates
        C-->>U: 예약 가능한 날짜 목록
        U->>C: GET /concerts/seats?date=2025-01-20
        C-->>U: 좌석 목록 + 상태
    end

    rect rgb(240, 255, 240)
        Note over U,R: Phase 3: 좌석 예약
        U->>R: POST /reservations
        R-->>U: 임시 예약 완료 (5분 타이머)
    end

    rect rgb(255, 240, 245)
        Note over U,P: Phase 4: 결제
        U->>P: POST /payment
        P-->>U: 결제 완료 + 예약 확정
    end
```

---

## 2. 대기열 토큰 발급

```mermaid
sequenceDiagram
    autonumber
    participant Client as 클라이언트
    participant Controller as QueueController
    participant Service as QueueService
    participant Repo as QueueTokenRepository
    participant DB as Database

    Client->>Controller: POST /queue/token<br/>{userId: "user123"}
    Controller->>Service: issueToken(request)
    
    Service->>Repo: findByUserId("user123")
    Repo->>DB: SELECT * FROM queue_token WHERE user_id = ?
    DB-->>Repo: 기존 토큰 또는 null
    
    alt 기존 유효 토큰 존재
        Repo-->>Service: QueueToken (ACTIVE/WAITING)
        Service-->>Controller: 기존 토큰 반환
    else 신규 발급 필요
        Service->>Repo: countActive()
        Repo->>DB: SELECT COUNT(*) WHERE status = 'ACTIVE'
        DB-->>Repo: 활성 토큰 수
        
        alt 활성 토큰 < 100
            Service->>Service: position = 0 (바로 활성화)
            Service->>Service: token.activate(expiresAt)
        else 활성 토큰 >= 100
            Service->>Repo: countWaiting()
            Service->>Service: position = waitingCount + 1
        end
        
        Service->>Repo: save(newToken)
        Repo->>DB: INSERT INTO queue_token
        DB-->>Repo: OK
        Repo-->>Service: QueueToken
    end
    
    Service-->>Controller: QueueTokenResponse
    Controller-->>Client: 200 OK<br/>{token, position, expiresIn}
```

---

## 3. 좌석 예약 (임시 배정)

```mermaid
sequenceDiagram
    autonumber
    participant Client as 클라이언트
    participant Controller as ReservationController
    participant UseCase as ReserveSeatUseCase
    participant QueueSvc as QueueService
    participant SeatRepo as SeatRepository
    participant ResvRepo as ReservationRepository
    participant DB as Database

    Client->>Controller: POST /reservations<br/>Header: X-QUEUE-TOKEN
    Controller->>UseCase: execute(request, token)
    
    rect rgb(255, 245, 238)
        Note over UseCase,QueueSvc: 1. 토큰 검증
        UseCase->>QueueSvc: validateToken(token)
        QueueSvc->>DB: SELECT * FROM queue_token WHERE token = ?
        
        alt 유효하지 않은 토큰
            QueueSvc-->>UseCase: BusinessException(401)
            UseCase-->>Controller: 401 Unauthorized
            Controller-->>Client: 401 Error
        else 대기 중 토큰
            QueueSvc-->>UseCase: BusinessException(403)
            UseCase-->>Controller: 403 Forbidden
            Controller-->>Client: 403 Error
        end
    end
    
    rect rgb(240, 255, 240)
        Note over UseCase,DB: 2. 좌석 조회 및 락 획득
        UseCase->>SeatRepo: findByConcertDateAndSeatNumberWithLock(date, seatNumber)
        SeatRepo->>DB: SELECT * FROM seat WHERE ... FOR UPDATE (Optimistic Lock)
        
        alt 좌석 없음
            DB-->>SeatRepo: null
            SeatRepo-->>UseCase: Optional.empty()
            UseCase-->>Controller: BusinessException(404)
            Controller-->>Client: 404 Not Found
        end
        
        DB-->>SeatRepo: Seat 엔티티
        SeatRepo-->>UseCase: Seat
    end
    
    rect rgb(255, 255, 240)
        Note over UseCase,DB: 3. 좌석 상태 확인 및 예약
        UseCase->>UseCase: seat.isExpired()?
        
        alt 만료된 임시 배정
            UseCase->>UseCase: seat.release()
        end
        
        alt 예약 불가 상태
            UseCase-->>Controller: IllegalStateException
            Controller-->>Client: 400 Bad Request
        end
        
        UseCase->>UseCase: seat.reserve(userId, expiresAt)
        UseCase->>SeatRepo: save(seat)
        SeatRepo->>DB: UPDATE seat SET status='TEMP_HELD', version=version+1
    end
    
    rect rgb(248, 248, 255)
        Note over UseCase,DB: 4. 예약 엔티티 생성
        UseCase->>UseCase: Reservation.create(...)
        UseCase->>ResvRepo: save(reservation)
        ResvRepo->>DB: INSERT INTO reservation
        DB-->>ResvRepo: OK
    end
    
    UseCase-->>Controller: SeatReserveResponse
    Controller-->>Client: 200 OK<br/>{seatNumber, tempHoldExpires, status}
```

---

## 4. 결제 처리

```mermaid
sequenceDiagram
    autonumber
    participant Client as 클라이언트
    participant Controller as PaymentController
    participant UseCase as ProcessPaymentUseCase
    participant QueueSvc as QueueService
    participant PointSvc as PointService
    participant ResvRepo as ReservationRepository
    participant SeatRepo as SeatRepository
    participant PayRepo as PaymentRepository
    participant DB as Database

    Client->>Controller: POST /payment<br/>Header: X-QUEUE-TOKEN
    Controller->>UseCase: execute(request, token)
    
    rect rgb(255, 245, 238)
        Note over UseCase,QueueSvc: 1. 토큰 검증
        UseCase->>QueueSvc: validateToken(token)
    end
    
    rect rgb(240, 255, 240)
        Note over UseCase,DB: 2. 예약 조회
        UseCase->>ResvRepo: findByUserIdAndConcertDateAndSeatNumberAndStatus(...)
        ResvRepo->>DB: SELECT * FROM reservation WHERE ...
        
        alt 예약 없음
            DB-->>ResvRepo: null
            UseCase-->>Controller: BusinessException(404)
            Controller-->>Client: 404 Not Found
        end
        
        DB-->>ResvRepo: Reservation
        
        alt 예약 만료됨
            UseCase->>UseCase: reservation.isExpired() == true
            UseCase-->>Controller: BusinessException(400)
            Controller-->>Client: 400 Bad Request
        end
    end
    
    rect rgb(255, 255, 240)
        Note over UseCase,DB: 3. 포인트 차감 (비관적 락)
        UseCase->>PointSvc: usePoint(userId, amount)
        PointSvc->>DB: SELECT * FROM point_balance WHERE user_id = ? FOR UPDATE
        
        alt 잔액 부족
            PointSvc-->>UseCase: BusinessException(400)
            UseCase-->>Controller: 400 Bad Request
            Controller-->>Client: 400 Insufficient Balance
        end
        
        PointSvc->>DB: UPDATE point_balance SET balance = balance - ?
    end
    
    rect rgb(248, 248, 255)
        Note over UseCase,DB: 4. 좌석 확정
        UseCase->>SeatRepo: findById(seatId)
        SeatRepo->>DB: SELECT * FROM seat
        DB-->>SeatRepo: Seat
        UseCase->>UseCase: seat.confirm()
        UseCase->>SeatRepo: save(seat)
        SeatRepo->>DB: UPDATE seat SET status='RESERVED'
    end
    
    rect rgb(255, 240, 245)
        Note over UseCase,DB: 5. 예약 확정
        UseCase->>UseCase: reservation.confirm()
        UseCase->>ResvRepo: save(reservation)
        ResvRepo->>DB: UPDATE reservation SET status='CONFIRMED'
    end
    
    rect rgb(240, 248, 255)
        Note over UseCase,DB: 6. 결제 내역 생성
        UseCase->>UseCase: Payment.create(...)
        UseCase->>PayRepo: save(payment)
        PayRepo->>DB: INSERT INTO payment
    end
    
    rect rgb(250, 250, 250)
        Note over UseCase,QueueSvc: 7. 토큰 만료
        UseCase->>QueueSvc: expireToken(token)
        QueueSvc->>DB: UPDATE queue_token SET status='EXPIRED'
    end
    
    UseCase-->>Controller: PaymentResponse
    Controller-->>Client: 200 OK<br/>{paymentId, remainingPoints, status}
```

---

## 5. 만료 처리 스케줄러

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as ExpirationScheduler
    participant ResvRepo as ReservationRepository
    participant SeatRepo as SeatRepository
    participant TokenRepo as QueueTokenRepository
    participant DB as Database

    rect rgb(255, 250, 240)
        Note over Scheduler,DB: Task 1: 만료된 임시 예약 해제 (1분마다)
        Scheduler->>ResvRepo: findByStatusAndReservedUntilBefore(TEMP_HELD, now)
        ResvRepo->>DB: SELECT * FROM reservation WHERE status='TEMP_HELD' AND reserved_until < NOW()
        DB-->>ResvRepo: List<Reservation>
        
        loop 각 만료 예약
            Scheduler->>Scheduler: reservation.expire()
            Scheduler->>ResvRepo: save(reservation)
            Scheduler->>SeatRepo: findById(seatId)
            Scheduler->>Scheduler: seat.release()
            Scheduler->>SeatRepo: save(seat)
        end
    end
    
    rect rgb(240, 255, 240)
        Note over Scheduler,DB: Task 2: 대기열 토큰 활성화 (30초마다)
        Scheduler->>TokenRepo: countActive()
        TokenRepo->>DB: SELECT COUNT(*) WHERE status='ACTIVE'
        DB-->>TokenRepo: activeCount
        
        alt activeCount < 100
            Scheduler->>TokenRepo: findTopNByStatusOrderByCreatedAtAsc(WAITING, 100-activeCount)
            TokenRepo->>DB: SELECT * FROM queue_token WHERE status='WAITING' ORDER BY created_at LIMIT ?
            DB-->>TokenRepo: List<QueueToken>
            
            loop 각 대기 토큰
                Scheduler->>Scheduler: token.activate(expiresAt)
                Scheduler->>TokenRepo: save(token)
            end
        end
    end
    
    rect rgb(248, 248, 255)
        Note over Scheduler,DB: Task 3: 만료 토큰 정리 (5분마다)
        Scheduler->>TokenRepo: findByStatusAndExpiresAtBefore(ACTIVE, now)
        TokenRepo->>DB: SELECT * FROM queue_token WHERE status='ACTIVE' AND expires_at < NOW()
        DB-->>TokenRepo: List<QueueToken>
        
        loop 각 만료 토큰
            Scheduler->>Scheduler: token.expire()
            Scheduler->>TokenRepo: save(token)
        end
    end
```

---

## 📊 상태 전이 다이어그램

### 좌석 상태 (SeatStatus)
```mermaid
stateDiagram-v2
    [*] --> AVAILABLE: 초기 상태
    AVAILABLE --> TEMP_HELD: 임시 예약
    TEMP_HELD --> RESERVED: 결제 완료
    TEMP_HELD --> AVAILABLE: 타임아웃/취소
    RESERVED --> [*]: 공연 종료
```

### 예약 상태 (ReservationStatus)
```mermaid
stateDiagram-v2
    [*] --> TEMP_HELD: 예약 생성
    TEMP_HELD --> CONFIRMED: 결제 완료
    TEMP_HELD --> EXPIRED: 5분 타임아웃
    TEMP_HELD --> CANCELLED: 사용자 취소
    CONFIRMED --> [*]: 예약 완료
    EXPIRED --> [*]
    CANCELLED --> [*]
```

### 토큰 상태 (TokenStatus)
```mermaid
stateDiagram-v2
    [*] --> WAITING: 토큰 발급
    WAITING --> ACTIVE: 대기열 통과
    ACTIVE --> EXPIRED: 시간 만료/결제 완료
    EXPIRED --> [*]
```
