package kr.hhplus.be.server.interfaces.api.queue;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.application.queue.QueueService;
import kr.hhplus.be.server.common.exception.ProblemDetail;
import kr.hhplus.be.server.interfaces.api.queue.dto.QueueTokenRequest;
import kr.hhplus.be.server.interfaces.api.queue.dto.QueueTokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 대기열 관리 API
 */
@Tag(name = "Queue", description = "대기열 관리 API")
@RestController
@RequestMapping("/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * 대기열 토큰 발급
     * POST /queue/token
     */
    @Operation(
            summary = "대기열 토큰 발급",
            description = "사용자를 대기열에 등록하고 토큰을 발급합니다. 이 엔드포인트는 인증이 필요하지 않습니다.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 발급 성공",
                    content = @Content(schema = @Schema(implementation = QueueTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "type": "https://api.concert.com/problems/invalid-request",
                                      "title": "잘못된 요청",
                                      "status": 400,
                                      "detail": "userId는 필수 항목입니다.",
                                      "instance": "/queue/token",
                                      "timestamp": "2025-12-03T10:30:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping("/token")
    public ResponseEntity<QueueTokenResponse> issueToken(@RequestBody QueueTokenRequest request) {
        QueueTokenResponse response = queueService.issueToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 대기열 상태 조회
     * GET /queue/status
     */
    @Operation(
            summary = "대기열 상태 조회",
            description = "현재 대기열 상태 및 순서를 확인합니다.",
            security = @SecurityRequirement(name = "X-QUEUE-TOKEN")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "대기열 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = QueueTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "type": "https://api.concert.com/problems/invalid-token",
                                      "title": "유효하지 않은 토큰",
                                      "status": 401,
                                      "detail": "제공된 대기열 토큰이 유효하지 않거나 만료되었습니다.",
                                      "instance": "/queue/status",
                                      "timestamp": "2025-12-03T10:30:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping("/status")
    public ResponseEntity<QueueTokenResponse> getStatus(
            @Parameter(description = "대기열 토큰", required = true)
            @RequestHeader("X-QUEUE-TOKEN") String token
    ) {
        QueueTokenResponse response = queueService.getTokenStatus(token);
        return ResponseEntity.ok(response);
    }
}
