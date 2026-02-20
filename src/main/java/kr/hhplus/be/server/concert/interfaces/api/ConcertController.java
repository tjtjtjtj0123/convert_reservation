package kr.hhplus.be.server.concert.interfaces.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.concert.application.service.ConcertService;
import kr.hhplus.be.server.concert.interfaces.api.dto.AvailableDatesResponse;
import kr.hhplus.be.server.concert.interfaces.api.dto.ConcertRankingResponse;
import kr.hhplus.be.server.concert.interfaces.api.dto.SeatListResponse;
import kr.hhplus.be.server.shared.common.exception.ProblemDetail;
import kr.hhplus.be.server.queue.application.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 공연 조회 API Controller (Interface Layer)
 * 도메인 기반 클린 아키텍처
 */
@Tag(name = "Concert", description = "공연 조회 API")
@RestController
@RequestMapping("/concerts")
public class ConcertController {

    private final ConcertService concertService;
    private final ConcertRankingService concertRankingService;
    private final QueueService queueService;

    public ConcertController(ConcertService concertService, ConcertRankingService concertRankingService, QueueService queueService) {
        this.concertService = concertService;
        this.concertRankingService = concertRankingService;
        this.queueService = queueService;
    }

    /**
     * 예약 가능한 날짜 조회
     * GET /concerts/available-dates
     */
    @Operation(
            summary = "예약 가능한 날짜 조회",
            description = "예약 가능한 공연 날짜 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AvailableDatesResponse.class))
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
                    description = "대기 순서 미도달",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping("/available-dates")
    public ResponseEntity<AvailableDatesResponse> getAvailableDates(
            @Parameter(description = "대기열 토큰", required = true)
            @RequestHeader("X-QUEUE-TOKEN") String token
    ) {
        queueService.validateToken(token);
        AvailableDatesResponse response = concertService.getAvailableDates();
        return ResponseEntity.ok(response);
    }

    /**
     * 좌석 목록 조회
     * GET /concerts/seats?date={date}
     */
    @Operation(
            summary = "좌석 목록 조회",
            description = "특정 날짜의 좌석 목록 및 예약 상태를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SeatListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 날짜 형식",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
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
                    description = "대기 순서 미도달",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping("/seats")
    public ResponseEntity<SeatListResponse> getSeats(
            @Parameter(description = "대기열 토큰", required = true)
            @RequestHeader("X-QUEUE-TOKEN") String token,
            @Parameter(description = "조회할 날짜 (YYYY-MM-DD)", required = true, example = "2025-12-25")
            @RequestParam String date
    ) {
        queueService.validateToken(token);
        SeatListResponse response = concertService.getSeats(date);
        return ResponseEntity.ok(response);
    }

    /**
     * 빠른 매진 랭킹 조회
     * GET /concerts/ranking
     */
    @Operation(
            summary = "빠른 매진 랭킹 조회",
            description = "예약이 빠르게 진행되는 콘서트 상위 랭킹을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "랭킹 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConcertRankingResponse.class)
                    )
            )
    })
    @GetMapping("/ranking")
    public ResponseEntity<ConcertRankingResponse> getRanking(
            @Parameter(description = "상위 N개 조회 (기본 10)", example = "10")
            @RequestParam(defaultValue = "10") int topN
    ) {
        ConcertRankingResponse response = concertRankingService.getTopRanking(topN);
        return ResponseEntity.ok(response);
    }
}
