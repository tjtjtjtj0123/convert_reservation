package kr.hhplus.be.server.queue.infrastructure.scheduler;

import kr.hhplus.be.server.queue.application.service.RedisQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 대기열 스케줄러
 *
 * 대기열(Sorted Set)에서 활성 슬롯 여유분만큼 토큰을 활성화.
 * Redis TTL로 자동 만료되므로 별도 만료 스케줄러 불필요.
 */
@Component
public class RedisQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueScheduler.class);

    private final RedisQueueService redisQueueService;

    public RedisQueueScheduler(RedisQueueService redisQueueService) {
        this.redisQueueService = redisQueueService;
    }

    /**
     * 대기 → 활성 전환 (30초마다 실행)
     */
    @Scheduled(fixedRate = 30000)
    public void activateWaitingTokens() {
        try {
            redisQueueService.activateWaitingTokens();
        } catch (Exception e) {
            log.error("Redis 대기열 활성화 실패", e);
        }
    }
}
