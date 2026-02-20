package kr.hhplus.be.server.application.concert;

import kr.hhplus.be.server.concert.application.service.ConcertRankingService;
import kr.hhplus.be.server.concert.domain.repository.ConcertRankingRepository;
import kr.hhplus.be.server.concert.domain.repository.ConcertRankingRepository.ConcertRankingEntry;
import kr.hhplus.be.server.concert.interfaces.api.dto.ConcertRankingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 콘서트 매진 랭킹 서비스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("콘서트 매진 랭킹 서비스 단위 테스트")
class ConcertRankingServiceTest {

    @Mock
    private ConcertRankingRepository rankingRepository;

    @InjectMocks
    private ConcertRankingService concertRankingService;

    @Test
    @DisplayName("좌석 예약 시 매진 랭킹을 업데이트한다")
    void onSeatReserved_updatesRanking() {
        // Given
        String concertDate = "2025-12-25";

        // When
        concertRankingService.onSeatReserved(concertDate);

        // Then
        verify(rankingRepository, times(1)).incrementReservationCount(concertDate);
    }

    @Test
    @DisplayName("빠른 매진 랭킹 Top N을 조회한다")
    void getTopRanking_returnsTopN() {
        // Given
        int topN = 3;
        List<ConcertRankingEntry> mockEntries = List.of(
                new ConcertRankingEntry("2025-12-25", 50, 1),
                new ConcertRankingEntry("2025-12-31", 30, 2),
                new ConcertRankingEntry("2025-12-24", 20, 3)
        );
        when(rankingRepository.getTopRanking(topN)).thenReturn(mockEntries);

        // When
        ConcertRankingResponse response = concertRankingService.getTopRanking(topN);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRankings()).hasSize(3);

        ConcertRankingResponse.RankingEntry first = response.getRankings().get(0);
        assertThat(first.getConcertDate()).isEqualTo("2025-12-25");
        assertThat(first.getReservationCount()).isEqualTo(50);
        assertThat(first.getRank()).isEqualTo(1);

        ConcertRankingResponse.RankingEntry second = response.getRankings().get(1);
        assertThat(second.getConcertDate()).isEqualTo("2025-12-31");
        assertThat(second.getReservationCount()).isEqualTo(30);
        assertThat(second.getRank()).isEqualTo(2);

        verify(rankingRepository, times(1)).getTopRanking(topN);
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
    void getTopRanking_emptyWhenNoData() {
        // Given
        when(rankingRepository.getTopRanking(10)).thenReturn(List.of());

        // When
        ConcertRankingResponse response = concertRankingService.getTopRanking(10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRankings()).isEmpty();
    }

    @Test
    @DisplayName("특정 콘서트의 랭킹 정보를 조회한다")
    void getConcertRanking_returnsRankingInfo() {
        // Given
        String concertDate = "2025-12-25";
        when(rankingRepository.getRank(concertDate)).thenReturn(0L);
        when(rankingRepository.getScore(concertDate)).thenReturn(50.0);

        // When
        ConcertRankingResponse.RankingEntry entry = concertRankingService.getConcertRanking(concertDate);

        // Then
        assertThat(entry).isNotNull();
        assertThat(entry.getConcertDate()).isEqualTo("2025-12-25");
        assertThat(entry.getReservationCount()).isEqualTo(50);
        assertThat(entry.getRank()).isEqualTo(0);
    }

    @Test
    @DisplayName("랭킹에 없는 콘서트 조회 시 기본값을 반환한다")
    void getConcertRanking_defaultWhenNotExists() {
        // Given
        String concertDate = "2025-12-25";
        when(rankingRepository.getRank(concertDate)).thenReturn(null);
        when(rankingRepository.getScore(concertDate)).thenReturn(0.0);

        // When
        ConcertRankingResponse.RankingEntry entry = concertRankingService.getConcertRanking(concertDate);

        // Then
        assertThat(entry.getReservationCount()).isEqualTo(0);
        assertThat(entry.getRank()).isEqualTo(0);
    }
}
