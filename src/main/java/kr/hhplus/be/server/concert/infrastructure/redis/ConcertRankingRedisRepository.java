package kr.hhplus.be.server.concert.infrastructure.redis;

import kr.hhplus.be.server.concert.domain.repository.ConcertRankingRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Redis Sorted Set 기반 콘서트 매진 랭킹 구현체
 *
 * 키: "ranking:soldout"
 * Member: 콘서트 날짜 (예: "2026-03-15")
 * Score: 예약된 좌석 수 (ZINCRBY로 원자적 증가)
 *
 * 조회: ZREVRANGE (예약 수 내림차순 = 빠른 매진 순)
 */
@Repository
public class ConcertRankingRedisRepository implements ConcertRankingRepository {

    private static final String RANKING_KEY = "ranking:soldout";

    private final StringRedisTemplate redisTemplate;

    public ConcertRankingRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementReservationCount(String concertDate) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, concertDate, 1);
    }

    @Override
    public List<ConcertRankingEntry> getTopRanking(int topN) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, topN - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConcertRankingEntry> entries = new ArrayList<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            entries.add(new ConcertRankingEntry(
                    tuple.getValue(),
                    tuple.getScore() != null ? tuple.getScore().longValue() : 0,
                    rank++
            ));
        }
        return entries;
    }

    @Override
    public Long getRank(String concertDate) {
        Long rank = redisTemplate.opsForZSet().reverseRank(RANKING_KEY, concertDate);
        return rank != null ? rank + 1 : null; // 0-based → 1-based
    }

    @Override
    public double getScore(String concertDate) {
        Double score = redisTemplate.opsForZSet().score(RANKING_KEY, concertDate);
        return score != null ? score : 0;
    }
}
