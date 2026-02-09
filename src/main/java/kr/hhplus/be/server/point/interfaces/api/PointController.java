package kr.hhplus.be.server.point.interfaces.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.shared.common.exception.ProblemDetail;
import kr.hhplus.be.server.point.application.service.PointService;
import kr.hhplus.be.server.point.interfaces.api.dto.PointBalanceResponse;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeRequest;
import kr.hhplus.be.server.point.interfaces.api.dto.PointChargeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 포인트 관리 API (Interface Layer)
 * 도메인 기반 클린 아키텍처
 */
@Tag(name = "Point", description = "포인트 관리 API")
@RestController
@RequestMapping("/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * 포인트 충전
     * POST /points/charge
     * 토큰 불필요
     */
    @Operation(
            summary = "포인트 충전",
            description = "사용자 포인트를 충전합니다. 이 엔드포인트는 인증이 필요하지 않습니다.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "충전 성공",
                    content = @Content(schema = @Schema(implementation = PointChargeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "type": "https://api.concert.com/problems/invalid-amount",
                                      "title": "잘못된 충전 금액",
                                      "status": 400,
                                      "detail": "충전 금액은 0보다 커야 합니다.",
                                      "instance": "/points/charge",
                                      "timestamp": "2025-12-03T10:30:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping("/charge")
    public ResponseEntity<PointChargeResponse> chargePoints(
            @RequestBody PointChargeRequest request
    ) {
        PointChargeResponse response = pointService.charge(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 잔액 조회
     * GET /points/balance?userId={userId}
     * 토큰 불필요
     */
    @Operation(
            summary = "포인트 잔액 조회",
            description = "사용자의 현재 포인트 잔액을 조회합니다. 이 엔드포인트는 인증이 필요하지 않습니다.",
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PointBalanceResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "type": "https://api.concert.com/problems/unauthorized-access",
                                      "title": "권한 없음",
                                      "status": 403,
                                      "detail": "다른 사용자의 포인트 정보를 조회할 수 없습니다.",
                                      "instance": "/points/balance",
                                      "timestamp": "2025-12-03T10:30:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping("/balance")
    public ResponseEntity<PointBalanceResponse> getBalance(
            @Parameter(description = "사용자 ID", required = true, example = "user-123")
            @RequestParam String userId
    ) {
        PointBalanceResponse response = pointService.getBalance(userId);
        return ResponseEntity.ok(response);
    }
}
