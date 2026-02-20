package kr.hhplus.be.server.concert.interfaces.api.dto;

import java.util.List;

/**
 * 콘서트 매진 랭킹 응답 DTO
 */
public class ConcertRankingResponse {

    private final List<RankingEntry> rankings;

    public ConcertRankingResponse(List<RankingEntry> rankings) {
        this.rankings = rankings;
    }

    public List<RankingEntry> getRankings() {
        return rankings;
    }

    /**
     * 개별 랭킹 엔트리
     */
    public static class RankingEntry {
        private final String concertDate;
        private final long reservationCount;
        private final long rank;

        public RankingEntry(String concertDate, long reservationCount, long rank) {
            this.concertDate = concertDate;
            this.reservationCount = reservationCount;
            this.rank = rank;
        }

        public String getConcertDate() {
            return concertDate;
        }

        public long getReservationCount() {
            return reservationCount;
        }

        public long getRank() {
            return rank;
        }
    }
}
