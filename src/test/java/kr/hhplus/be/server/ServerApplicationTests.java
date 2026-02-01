package kr.hhplus.be.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIf("isDockerAvailable")
class ServerApplicationTests {

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
