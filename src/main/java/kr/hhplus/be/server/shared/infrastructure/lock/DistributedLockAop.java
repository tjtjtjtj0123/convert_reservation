package kr.hhplus.be.server.shared.infrastructure.lock;

import kr.hhplus.be.server.shared.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 분산락 AOP
 * 
 * 핵심: @Transactional보다 먼저(바깥에서) 실행되어야 함
 * → 락 획득 → 트랜잭션 시작 → 로직 실행 → 트랜잭션 커밋 → 락 해제
 * 
 * @Order(Ordered.HIGHEST_PRECEDENCE)로 트랜잭션 AOP보다 우선 실행되도록 설정
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedLockAop {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAop.class);
    private static final String LOCK_PREFIX = "LOCK:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final RedissonClient redissonClient;

    public DistributedLockAop(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(kr.hhplus.be.server.shared.infrastructure.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // SpEL로 키 평가
        String key = LOCK_PREFIX + parseKey(
                distributedLock.key(),
                signature.getParameterNames(),
                joinPoint.getArgs()
        );

        RLock rLock = redissonClient.getLock(key);
        boolean acquired = false;

        try {
            // 락 획득 시도
            acquired = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!acquired) {
                throw new BusinessException(
                        "락 획득에 실패했습니다. key=" + key,
                        "lock-acquisition-failed",
                        409
                );
            }

            log.debug("🔒 분산락 획득 성공: {}", key);

            // 락 범위 내에서 비즈니스 로직 실행 (트랜잭션 포함)
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    "락 획득 중 인터럽트 발생. key=" + key,
                    "lock-interrupted",
                    500
            );
        } finally {
            // 락 해제 (현재 스레드가 소유한 경우에만)
            if (acquired && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("🔓 분산락 해제 완료: {}", key);
            }
        }
    }

    /**
     * SpEL 표현식 파싱
     */
    private String parseKey(String keyExpression, String[] paramNames, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        return PARSER.parseExpression(keyExpression).getValue(context, String.class);
    }
}
