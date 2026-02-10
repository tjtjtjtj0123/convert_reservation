package kr.hhplus.be.server.reservation.interfaces.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.shared.common.exception.ProblemDetail;
import kr.hhplus.be.server.reservation.application.service.ReservationService;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveRequest;
import kr.hhplus.be.server.reservation.interfaces.api.dto.SeatReserveResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 예약 관리 API Controller (Interface Layer)
 * 도메인 기반 클린 아키텍처
 */
@Tag(name = "Reservation", description = "예약 관리 API")
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 좌석 임시 예약
     * POST /reservations
     */
    @Operation(
            summary = "좌석 임시 예약",
            description = "좌석을 5분간 임시 예약합니다. 임시 예약 시간 내에 결제를 완료해야 합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "임시 예약 성공",
                    content = @Content(schema = @Schema(implementation = SeatReserveResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 예약된 좌석",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "type": "https://api.concert.com/problems/seat-already-reserved",
                                      "title": "좌석 예약 불가",
                                      "status": 400,
                                      "detail": "요청하신 좌석은 이미 예약되었거나 임시 예약 중입니다.",
                                      "instance": "/reservations",
                                      "timestamp": "2025-12-03T10:30:00Z"
                                    }
                                    """)
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
                    description = "좌석을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<SeatReserveResponse> reserveSeat(
            @Parameter(description = "대기열 토큰", required = true)
            @RequestHeader("X-QUEUE-TOKEN") String token,
            @RequestBody SeatReserveRequest request
    ) {
        SeatReserveResponse response = reservationService.reserveSeat(request, token);
        return ResponseEntity.ok(response);
    }
}
