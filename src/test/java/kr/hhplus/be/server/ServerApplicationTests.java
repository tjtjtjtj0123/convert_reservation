package kr.hhplus.be.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("isDockerAvailable")
class ServerApplicationTests {

	private static final GenericContainer<?> REDIS_CONTAINER =
			new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
					.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		if (!REDIS_CONTAINER.isRunning()) {
			REDIS_CONTAINER.start();
		}
		registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
		registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
	}

	@Test
	void contextLoads() {
	}

	static boolean isDockerAvailable() {
		try {
			ProcessBuilder pb = new ProcessBuilder("docker", "info");
			Process process = pb.start();
			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (Exception e) {
			return false;
		}
	}
}
