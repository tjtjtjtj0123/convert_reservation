package kr.hhplus.be.server.concert.application.service;

import kr.hhplus.be.server.concert.domain.repository.ConcertRankingRepository;
import kr.hhplus.be.server.concert.domain.repository.ConcertRankingRepository.ConcertRankingEntry;
import kr.hhplus.be.server.concert.interfaces.api.dto.ConcertRankingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 콘서트 매진 랭킹 서비스
 *
 * Redis Sorted Set 기반의 빠른 매진 랭킹 관리:
 * - 좌석 예약 시: ZINCRBY로 해당 콘서트의 예약 수 원자적 증가
 * - 랭킹 조회 시: ZREVRANGE로 예약이 빠르게 진행되는 콘서트 순 조회
 */
@Service
public class ConcertRankingService {

    private final ConcertRankingRepository rankingRepository;

    public ConcertRankingService(ConcertRankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    /**
     * 좌석 예약 시 매진 랭킹 업데이트
     * - ZINCRBY로 해당 콘서트 날짜의 예약 수를 1 증가
     * - Redis의 원자적 연산으로 동시성 안전
     */
    public void onSeatReserved(String concertDate) {
        rankingRepository.incrementReservationCount(concertDate);
    }

    /**
     * 빠른 매진 랭킹 Top N 조회
     *
     * @param topN 상위 N개 (기본 10)
     * @return 예약이 빠르게 진행되는 콘서트 순 랭킹
     */
    public ConcertRankingResponse getTopRanking(int topN) {
        List<ConcertRankingEntry> entries = rankingRepository.getTopRanking(topN);

        List<ConcertRankingResponse.RankingEntry> responseEntries = entries.stream()
                .map(entry -> new ConcertRankingResponse.RankingEntry(
                        entry.concertDate(),
                        entry.reservationCount(),
                        entry.rank()
                ))
                .toList();

        return new ConcertRankingResponse(responseEntries);
    }

    /**
     * 특정 콘서트의 랭킹 정보 조회
     */
    public ConcertRankingResponse.RankingEntry getConcertRanking(String concertDate) {
        Long rank = rankingRepository.getRank(concertDate);
        double score = rankingRepository.getScore(concertDate);

        return new ConcertRankingResponse.RankingEntry(
                concertDate,
                (long) score,
                rank != null ? rank : 0
        );
    }
}
