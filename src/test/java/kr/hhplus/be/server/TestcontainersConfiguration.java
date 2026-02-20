package kr.hhplus.be.server;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 기반 MySQL + Redis 설정.
 * @TestConfiguration이므로 자동 컴포넌트 스캔에서 제외됨.
 * 필요한 테스트에서 @Import(TestcontainersConfiguration.class) 로 명시적으로 사용.
 * Docker가 실행 중일 때만 사용 가능.
 */
@TestConfiguration
public class TestcontainersConfiguration {

	private static MySQLContainer<?> MYSQL_CONTAINER;
	private static GenericContainer<?> REDIS_CONTAINER;

	public static void startIfNeeded() {
		startMySQLIfNeeded();
		startRedisIfNeeded();
	}

	private static void startMySQLIfNeeded() {
		if (MYSQL_CONTAINER == null) {
			MYSQL_CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
				.withDatabaseName("hhplus")
				.withUsername("test")
				.withPassword("test");
			MYSQL_CONTAINER.start();

			System.setProperty("spring.datasource.url", MYSQL_CONTAINER.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC");
			System.setProperty("spring.datasource.username", MYSQL_CONTAINER.getUsername());
			System.setProperty("spring.datasource.password", MYSQL_CONTAINER.getPassword());
		}
	}

	private static void startRedisIfNeeded() {
		if (REDIS_CONTAINER == null) {
			REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
				.withExposedPorts(6379);
			REDIS_CONTAINER.start();

			System.setProperty("spring.data.redis.host", REDIS_CONTAINER.getHost());
			System.setProperty("spring.data.redis.port", REDIS_CONTAINER.getMappedPort(6379).toString());
		}
	}

	@PreDestroy
	public void preDestroy() {
		if (MYSQL_CONTAINER != null && MYSQL_CONTAINER.isRunning()) {
			MYSQL_CONTAINER.stop();
		}
		if (REDIS_CONTAINER != null && REDIS_CONTAINER.isRunning()) {
			REDIS_CONTAINER.stop();
		}
	}
}