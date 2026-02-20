package kr.hhplus.be.server.shared.infrastructure.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 분산락 어노테이션
 * 
 * 핵심 원칙:
 * - 락 획득 → 트랜잭션 시작 → 비즈니스 로직 → 트랜잭션 커밋 → 락 해제
 * - AOP에서 REQUIRES_NEW 트랜잭션으로 실행되어 락 범위 내에서 트랜잭션이 완료됨을 보장
 * 
 * key에 SpEL(Spring Expression Language) 사용 가능:
 *   @DistributedLock(key = "'seat:' + #date + ':' + #seatNumber")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 분산락 키 (SpEL 지원)
     * 예: "'reservation:' + #date + ':' + #seatNumber"
     */
    String key();

    /**
     * 락 획득 대기 시간 (기본 5초)
     */
    long waitTime() default 5L;

    /**
     * 락 점유 시간 (기본 3초, 이 시간이 지나면 자동 해제)
     */
    long leaseTime() default 3L;

    /**
     * 시간 단위 (기본 SECONDS)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
