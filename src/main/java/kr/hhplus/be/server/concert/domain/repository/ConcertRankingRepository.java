package kr.hhplus.be.server.concert.domain.repository;

import java.util.List;

/**
 * 콘서트 매진 랭킹 리포지토리 인터페이스 (Domain Layer)
 * Redis Sorted Set 기반의 빠른 매진 랭킹 관리
 */
public interface ConcertRankingRepository {

    /**
     * 콘서트 예약 수 증가 (매진 랭킹 업데이트)
     * 좌석이 예약될 때마다 호출
     *
     * @param concertDate 콘서트 날짜 (예: "2026-03-15")
     */
    void incrementReservationCount(String concertDate);

    /**
     * 매진 랭킹 Top N 조회
     * 예약이 빠르게 진행된 콘서트 순으로 조회
     *
     * @param topN 상위 N개
     * @return (콘서트 날짜, 예약 수) 리스트 (예약 수 내림차순)
     */
    List<ConcertRankingEntry> getTopRanking(int topN);

    /**
     * 특정 콘서트의 현재 랭킹 순위 조회
     *
     * @param concertDate 콘서트 날짜
     * @return 순위 (0-based), 없으면 null
     */
    Long getRank(String concertDate);

    /**
     * 특정 콘서트의 현재 예약 수 조회
     *
     * @param concertDate 콘서트 날짜
     * @return 예약 수, 없으면 0
     */
    double getScore(String concertDate);

    /**
     * 매진 랭킹 엔트리 (Value Object)
     */
    record ConcertRankingEntry(
            String concertDate,
            long reservationCount,
            long rank
    ) {}
}
