package kr.hhplus.be.server.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 설정
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // 보안 스키마 이름
        String securitySchemeName = "X-QUEUE-TOKEN";

        // 보안 요구사항
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(securitySchemeName);

        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 서버"),
                        new Server().url("https://api.concert.com").description("운영 서버")
                ))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, securityScheme())
                )
                .addSecurityItem(securityRequirement);
    }

    private Info apiInfo() {
        return new Info()
                .title("Concert Booking Service")
                .version("1.0.0")
                .description("""
                        # 공연 예매 서비스 API 명세
                        
                        ## 인증/인가 플로우
                        
                        ### 1. 대기열 토큰 발급
                        - 사용자는 먼저 `/queue/token` 엔드포인트를 통해 대기열 토큰을 발급받아야 합니다.
                        - 토큰은 사용자의 대기열 순서와 만료 시간 정보를 포함합니다.
                        
                        ### 2. 토큰 기반 인증
                        - 대부분의 API는 `X-QUEUE-TOKEN` 헤더에 유효한 토큰을 요구합니다.
                        - 토큰이 없거나 유효하지 않은 경우 401 Unauthorized 응답을 받습니다.
                        - 예외: `/queue/token`, `/points/charge` 엔드포인트는 토큰 불필요
                        
                        ### 3. 대기열 상태 확인
                        - 토큰 발급 후 `/queue/status`를 통해 현재 대기 순서를 확인할 수 있습니다.
                        - 대기 순서가 되면 서비스 이용이 가능합니다.
                        
                        ### 4. 권한 실패 시나리오
                        - **401 Unauthorized**: 토큰이 없거나, 만료되었거나, 유효하지 않은 경우
                        - **403 Forbidden**: 토큰은 유효하지만 대기 순서가 아직 되지 않은 경우
                        - **404 Not Found**: 요청한 리소스에 접근 권한이 없는 경우 (다른 사용자의 예약 등)
                        
                        ### 5. 토큰 만료 및 갱신
                        - 토큰은 발급 후 일정 시간 후 만료됩니다.
                        - 만료된 토큰으로 요청 시 새로운 토큰을 발급받아야 합니다.
                        """);
    }

    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-QUEUE-TOKEN")
                .description("""
                        대기열 토큰 기반 인증
                        
                        **획득 방법**: POST /queue/token 엔드포인트를 통해 발급
                        
                        **사용 방법**: HTTP 요청 헤더에 `X-QUEUE-TOKEN: {token}` 형식으로 포함
                        
                        **유효성 검증**:
                        - 토큰 형식이 올바른지 확인
                        - 토큰이 만료되지 않았는지 확인
                        - 사용자의 대기 순서가 되었는지 확인
                        
                        **만료 처리**: 토큰 만료 시 401 응답과 함께 재발급 필요 메시지 반환
                        """);
    }
}
