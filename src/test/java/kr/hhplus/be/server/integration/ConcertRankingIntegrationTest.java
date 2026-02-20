package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.concert.interfaces.api.dto.ConcertRankingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 콘서트 매진 랭킹 통합 테스트
 * Redis Testcontainer 기반
 */
@DisplayName("콘서트 매진 랭킹 통합 테스트")
class ConcertRankingIntegrationTest extends BaseRedisIntegrationTest {

    @Autowired
    private ConcertRankingService concertRankingService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        redisTemplate.delete("ranking:soldout");
    }

    @Test
    @DisplayName("좌석 예약 시 매진 랭킹이 업데이트된다")
    void onSeatReserved_updatesRedisRanking() {
        // When
        concertRankingService.onSeatReserved("2025-12-25");
        concertRankingService.onSeatReserved("2025-12-25");
        concertRankingService.onSeatReserved("2025-12-31");

        // Then
        ConcertRankingResponse response = concertRankingService.getTopRanking(10);
        assertThat(response.getRankings()).hasSize(2);

        // 12/25가 2건으로 1위
        ConcertRankingResponse.RankingEntry first = response.getRankings().get(0);
        assertThat(first.getConcertDate()).isEqualTo("2025-12-25");
        assertThat(first.getReservationCount()).isEqualTo(2);
        assertThat(first.getRank()).isEqualTo(1);

        // 12/31이 1건으로 2위
        ConcertRankingResponse.RankingEntry second = response.getRankings().get(1);
        assertThat(second.getConcertDate()).isEqualTo("2025-12-31");
        assertThat(second.getReservationCount()).isEqualTo(1);
        assertThat(second.getRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("Top N 조회 시 N개만 반환된다")
    void getTopRanking_limitsToTopN() {
        // Given - 5개 콘서트 등록
        concertRankingService.onSeatReserved("2025-12-01");
        concertRankingService.onSeatReserved("2025-12-02");
        concertRankingService.onSeatReserved("2025-12-02");
        concertRankingService.onSeatReserved("2025-12-03");
        concertRankingService.onSeatReserved("2025-12-03");
        concertRankingService.onSeatReserved("2025-12-03");
        concertRankingService.onSeatReserved("2025-12-04");
        concertRankingService.onSeatReserved("2025-12-05");

        // When - Top 3만 요청
        ConcertRankingResponse response = concertRankingService.getTopRanking(3);

        // Then
        assertThat(response.getRankings()).hasSize(3);
        assertThat(response.getRankings().get(0).getConcertDate()).isEqualTo("2025-12-03");
        assertThat(response.getRankings().get(0).getReservationCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("특정 콘서트의 랭킹 정보를 조회한다")
    void getConcertRanking_returnsCorrectInfo() {
        // Given
        concertRankingService.onSeatReserved("2025-12-25");
        concertRankingService.onSeatReserved("2025-12-25");
        concertRankingService.onSeatReserved("2025-12-25");
        concertRankingService.onSeatReserved("2025-12-31");

        // When
        ConcertRankingResponse.RankingEntry entry = concertRankingService.getConcertRanking("2025-12-25");

        // Then
        assertThat(entry.getConcertDate()).isEqualTo("2025-12-25");
        assertThat(entry.getReservationCount()).isEqualTo(3);
        assertThat(entry.getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
    void getTopRanking_returnsEmptyWhenNoData() {
        // When
        ConcertRankingResponse response = concertRankingService.getTopRanking(10);

        // Then
        assertThat(response.getRankings()).isEmpty();
    }

    @Test
    @DisplayName("다수 예약이 원자적으로 카운트된다")
    void onSeatReserved_atomicIncrement() {
        // Given & When - 동일 콘서트에 100회 예약
        for (int i = 0; i < 100; i++) {
            concertRankingService.onSeatReserved("2025-12-25");
        }

        // Then
        ConcertRankingResponse.RankingEntry entry = concertRankingService.getConcertRanking("2025-12-25");
        assertThat(entry.getReservationCount()).isEqualTo(100);
    }
}
