# Kafka 기초 학습 및 활용 문서

## 1. Kafka란?

Apache Kafka는 **대규모 실시간 데이터 스트리밍을 위한 분산 메시징 플랫폼**입니다.

LinkedIn에서 개발되어 2011년 오픈소스로 공개되었으며, 현재는 Apache Software Foundation에서 관리하고 있습니다.
높은 처리량(Throughput), 낮은 지연시간(Low Latency), 높은 가용성(High Availability)을 제공하여 대규모 분산 시스템에서 널리 사용됩니다.

---

## 2. 왜 Kafka를 사용하는가?

### 기존 이벤트 방식의 한계

Spring의 `ApplicationEventPublisher` + `@TransactionalEventListener` + `@Async`를 사용하면:

- 서비스 내부에서 이벤트 기반 관심사 분리가 가능합니다.
- 하지만 **외부 서비스(데이터 플랫폼 등)의 장애 시 재전송 책임이 우리에게** 있습니다.
- 애플리케이션이 재시작되면 **메모리에 있던 이벤트가 유실**됩니다.

### Kafka를 사용하면 해결되는 점

| 문제 | Kafka 해결 방안 |
|------|----------------|
| 외부 서비스 장애 시 메시지 유실 | 메시지가 브로커에 **영속화**되어 장애 복구 후 재소비 가능 |
| 재전송 로직 구현 부담 | Consumer가 **자동으로 미처리 메시지부터** 재시작 |
| 단일 서버 장애 | **클러스터 기반 Replication**으로 고가용성 보장 |
| 처리량 한계 | **파티션 기반 병렬 처리**로 수평 확장 가능 |
| 서비스 간 강결합 | Producer/Consumer 분리로 **완전한 비동기 디커플링** |

---

## 3. Kafka의 장단점

### 장점

1. **높은 처리량 (High Throughput)**
   - 초당 수백만 건의 메시지 처리 가능
   - 배치 전송, 압축, Zero-copy 등 최적화 기법 적용

2. **메시지 영속성 (Durability)**
   - 메시지를 디스크에 저장하여 유실 방지
   - Replication을 통한 데이터 안정성 확보

3. **수평 확장성 (Scalability)**
   - 브로커 추가만으로 처리 용량 확장
   - 파티션 수 조정으로 병렬 처리 제어

4. **고가용성 (High Availability)**
   - 클러스터 구성으로 단일 장애점(SPOF) 제거
   - Leader-Follower Replication으로 자동 장애 복구

5. **유연한 소비 모델**
   - Consumer Group을 통한 독립적 소비
   - Offset 기반 재처리 가능

### 단점

1. **운영 복잡성**: Zookeeper/KRaft, 브로커, 토픽 관리 필요
2. **학습 곡선**: 파티션, 오프셋, 리밸런싱 등 개념 이해 필요
3. **실시간 처리 지연**: 배치 기반이므로 극도로 낮은 지연이 필요한 경우 부적합
4. **메시지 순서 보장 제한**: 파티션 내에서만 순서 보장, 전역 순서 보장 불가

---

## 4. Kafka 핵심 구성 요소

### 4.1 Producer & Consumer

```
Producer ──► Kafka Broker ──► Consumer
(메시지 발행)   (메시지 저장)    (메시지 소비)
```

- **Producer**: 메시지를 Kafka 브로커에 발행(Publish)하는 주체
- **Consumer**: 브로커에 저장된 메시지를 읽어(Subscribe) 처리하는 주체
  - Offset을 관리하여 어디까지 처리했는지 추적
  - 장애 시 마지막 커밋된 Offset부터 재시작

### 4.2 Broker

- Kafka 서버의 단위
- Producer로부터 메시지를 받아 Offset을 지정하고 디스크에 저장
- Consumer의 읽기 요청에 응답하여 메시지 전송
- 특수 역할:
  - **Controller**: 브로커 모니터링, Leader 파티션 재분배
  - **Coordinator**: Consumer Group 관리, 리밸런싱 수행

### 4.3 Topic & Partition

```
Topic: "payment-success"
├── Partition 0: [msg1] [msg3] [msg5] → Consumer A
├── Partition 1: [msg2] [msg4] [msg6] → Consumer B
└── Partition 2: [msg7] [msg8] [msg9] → Consumer C
```

- **Topic**: 메시지를 분류하는 논리적 단위
- **Partition**: Topic의 물리적 분할 단위
  - 같은 파티션 내에서 **순서 보장**
  - 파티션 수만큼 **병렬 처리** 가능
  - 메시지 키의 해시값으로 파티션 결정 → 같은 키는 항상 같은 파티션

### 4.4 Consumer Group

- 동일 Topic을 여러 서비스가 독립적으로 소비할 수 있는 단위
- 같은 Group 내 Consumer들은 파티션을 분담하여 소비
- 다른 Group은 동일 메시지를 각각 독립적으로 소비

### 4.5 Replication

- 파티션의 복제본을 다른 브로커에 유지
- **Leader Replica**: 모든 읽기/쓰기 처리
- **Follower Replica**: Leader의 데이터를 복제, 장애 시 Leader로 승격

---

## 5. Kafka 핵심 기능

### 5.1 Commit & Offset 관리

| 방식 | 설명 | 특징 |
|------|------|------|
| Auto-commit | 주기적 자동 커밋 (기본 5초) | 간편하지만 유실/중복 가능성 |
| Manual-commit (Sync) | 처리 완료 후 동기 커밋 | 안정적이지만 처리량 저하 |
| Manual-commit (Async) | 처리 완료 후 비동기 커밋 | 처리량 우수, 추가 오류 처리 필요 |

### 5.2 Rebalancing

Consumer Group 내에서 파티션 소유권을 재배치하는 과정:
- 새로운 Consumer 추가/제거 시 발생
- 리밸런싱 중에는 메시지 소비가 중단됨

### 5.3 DLQ (Dead Letter Queue)

- 반복 재시도에도 처리 불가한 메시지를 별도 토픽에 격리
- 모니터링, 원인 분석, 재처리에 활용
- 정상 메시지 처리에 영향을 주지 않도록 격리

### 5.4 멱등성 (Idempotency)

Kafka는 At-Least-Once 전달을 보장하므로 같은 메시지가 중복 전달될 수 있습니다:
- **Unique ID 활용**: 메시지 ID 기반 중복 처리 방지
- **상태 기반 처리**: 특정 상태 전이만 허용하여 중복 영향 제거

### 5.5 Zero-Payload vs Full-Payload

| 방식 | 메시지 내용 | 장점 | 단점 |
|------|------------|------|------|
| Zero-Payload | ID만 포함 | 메시지 크기 최적화, 최신 데이터 보장 | 추가 DB 조회 필요 |
| Full-Payload | 모든 데이터 포함 | 별도 조회 불필요 | 과거 스냅샷 문제, 메시지 크기 증가 |

> 실무에서는 Zero-Payload 방식이 더 많이 사용됩니다.

---

## 6. 콘서트 예약 시스템에서의 Kafka 활용

### 현재 아키텍처 (ApplicationEvent 기반)

```
PaymentService
  └── @Transactional
       ├── 포인트 차감
       ├── 결제 정보 저장
       ├── 예약 확정
       └── 결제_완료_이벤트_발행 (ApplicationEventPublisher)

PaymentEventListener
  └── @TransactionalEventListener(AFTER_COMMIT)
       └── @Async
            └── 데이터 플랫폼 전송 (HTTP API 호출)
```

### 개선된 아키텍처 (Kafka 기반)

```
PaymentService
  └── @Transactional
       ├── 포인트 차감
       ├── 결제 정보 저장
       ├── 예약 확정
       └── 결제_완료_이벤트_발행 (ApplicationEventPublisher)

PaymentEventListener
  └── @TransactionalEventListener(AFTER_COMMIT)
       └── KafkaProducer.send("payment-success", event)

KafkaPaymentConsumer (데이터 플랫폼 서비스)
  └── @KafkaListener(topic = "payment-success")
       └── 데이터 플랫폼에 결제 정보 전송
```

### 개선 포인트

1. **데이터 플랫폼 장애 시에도 메시지가 Kafka에 영속화**되어 유실 방지
2. **재전송 로직 불필요** — Consumer가 알아서 미처리 메시지부터 재소비
3. **관심사 완전 분리** — Producer는 발행만 하면 되고, Consumer가 독립적으로 처리
4. **새로운 소비자 추가 용이** — 알림 서비스, 분석 서비스 등 새 Consumer Group만 추가하면 됨

---

## 7. Kafka 클러스터 구성

본 프로젝트에서는 `docker-compose.kafka.yml`을 통해 로컬에서 Kafka 클러스터를 구성합니다:

- **Zookeeper**: 1대 (클러스터 메타데이터 관리)
- **Kafka Broker**: 3대 (고가용성 확보)
  - broker1: `localhost:9092`
  - broker2: `localhost:9093`
  - broker3: `localhost:9094`

### 실행 방법

```bash
# Kafka 클러스터 시작
docker-compose -f docker-compose.kafka.yml up -d

# 상태 확인
docker-compose -f docker-compose.kafka.yml ps

# 토픽 생성
docker exec broker1 kafka-topics --create --topic payment-success \
  --bootstrap-server broker1:29092 --partitions 3 --replication-factor 2

# 토픽 목록 확인
docker exec broker1 kafka-topics --list --bootstrap-server broker1:29092

# 클러스터 중지
docker-compose -f docker-compose.kafka.yml down
```
