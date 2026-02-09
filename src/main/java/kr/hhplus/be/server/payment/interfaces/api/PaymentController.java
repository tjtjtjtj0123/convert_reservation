package kr.hhplus.be.server.payment.interfaces.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.shared.common.exception.ProblemDetail;
import kr.hhplus.be.server.payment.application.service.PaymentService;
import kr.hhplus.be.server.payment.interfaces.api.dto.PaymentRequest;
import kr.hhplus.be.server.payment.interfaces.api.dto.PaymentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 API (Interface Layer)
 * 도메인 기반 클린 아키텍처
 */
@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 결제
     * POST /payment
     */
    @Operation(
            summary = "결제",
            description = "임시 예약된 좌석에 대해 결제를 진행합니다. 포인트를 차감하고 예약을 확정합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "결제 성공",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잔액 부족 또는 임시 예약 만료",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                    @ExampleObject(name = "잔액 부족", value = """
                                            {
                                              "type": "https://api.concert.com/problems/insufficient-balance",
                                              "title": "잔액 부족",
                                              "status": 400,
                                              "detail": "결제에 필요한 포인트가 부족합니다. 현재 잔액: 1000, 필요 금액: 5000",
                                              "instance": "/payment",
                                              "timestamp": "2025-12-03T10:30:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "임시 예약 만료", value = """
                                            {
                                              "type": "https://api.concert.com/problems/reservation-expired",
                                              "title": "임시 예약 만료",
                                              "status": 400,
                                              "detail": "임시 예약이 만료되었습니다. 좌석을 다시 예약해 주세요.",
                                              "instance": "/payment",
                                              "timestamp": "2025-12-03T10:30:00Z"
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "대기 순서 미도달 또는 권한 없음",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "예약을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Parameter(description = "대기열 토큰", required = true)
            @RequestHeader("X-QUEUE-TOKEN") String token,
            @RequestBody PaymentRequest request
    ) {
        PaymentResponse response = paymentService.processPayment(request, token);
        return ResponseEntity.ok(response);
    }
}
