package kr.hhplus.be.server.integration.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 통합 테스트용 스케줄러 설정
 * @Scheduled 메서드의 자동 실행을 방지하되,
 * 스케줄러 빈 자체는 존재하도록 하여 수동 호출은 가능하게 합니다.
 */
@TestConfiguration
public class TestSchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        // 스케줄러가 존재하지만 자동 실행은 테스트에서 직접 제어
        return scheduler;
    }
}
