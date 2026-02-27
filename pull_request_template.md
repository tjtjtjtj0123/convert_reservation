### **커밋 설명**

- `c68b840` : @EnableAsync 활성화로 비동기 이벤트 처리 기반 구성
- `99ebd1c` : 이벤트 객체 생성 (PaymentSuccessEvent, ReservationCompletedEvent)
- `7768f73` : 이벤트 퍼블리셔, 리스너, DataPlatformSendService Mock 생성
- `4e5bc0c` : 핵심 로직과 부가 로직 이벤트 기반 분리 (PaymentService, ReservationService)
- `2fb1c88` : 이벤트 퍼블리셔 Mock 적용 및 이벤트 리스너 단위 테스트 추가
- `312611a` : 이벤트 기반 통합 테스트 및 awaitility 의존성 추가
- `972cdf9` : CH08 이벤트 기반 트랜잭션 분리 및 MSA 설계 문서 작성

---

### **과제 셀프 피드백**

- **[필수] Application Event 적용**: `@TransactionalEventListener(AFTER_COMMIT) + @Async`를 활용하여 결제/예약의 핵심 트랜잭션과 데이터 플랫폼 전송, 랭킹 업데이트 등 부가 로직을 분리했습니다.
- 이벤트 퍼블리셔를 래핑 클래스로 만들어 ApplicationEventPublisher 직접 의존을 방지하고 테스트 용이성을 높였습니다.
- 이벤트 리스너에서 try-catch로 외부 서비스 실패를 격리하여 핵심 로직에 영향이 없도록 했습니다.
- **[선택] MSA 설계**: 6개 서비스로 도메인을 분리하고, Choreography SAGA 패턴과 Outbox 패턴을 통한 분산 트랜잭션 해결방안을 설계했습니다.

### 기술적 성장

- `@TransactionalEventListener`의 `AFTER_COMMIT` phase가 트랜잭션 커밋 이후에만 이벤트를 처리하여 데이터 정합성을 보장하는 메커니즘을 학습
- 핵심 로직(결제, 예약)과 부가 로직(데이터 전송, 랭킹)의 관심사 분리를 통한 장애 격리 패턴 체득
- Spring Event 기반 구현이 Kafka 기반 MSA로 자연스럽게 전환 가능한 아키텍처 설계 방법론 습득
- SAGA 패턴(Choreography/Orchestration)과 Outbox 패턴의 트레이드오프 분석