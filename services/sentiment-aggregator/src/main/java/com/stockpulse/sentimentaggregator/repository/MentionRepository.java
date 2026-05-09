package com.stockpulse.sentimentaggregator.repository;

import com.stockpulse.common.model.MentionEvent;
import com.stockpulse.sentimentaggregator.entity.MentionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MentionRepository extends JpaRepository<MentionEntity, Long> {

    @Query(value = """
        SELECT ticker, COUNT(*) AS mention_count,
               AVG(sentiment_score * influencer_weight) AS weighted_sentiment,
               SUM(upvotes) AS total_upvotes
        FROM mentions
        WHERE time >= :since
          AND pe_ratio > 0 AND pe_ratio <= :maxPe
        GROUP BY ticker
        ORDER BY weighted_sentiment DESC, mention_count DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopTickersByWindow(
            @Param("since")  Instant since,
            @Param("maxPe")  double maxPe,
            @Param("limit")  int limit
    );

    @Query(value = """
        SELECT time_bucket(:bucket::interval, time) AS bucket,
               COUNT(*) AS mention_count,
               AVG(sentiment_score * influencer_weight) AS weighted_sentiment
        FROM mentions
        WHERE ticker = :ticker AND time >= :since
        GROUP BY bucket
        ORDER BY bucket ASC
        """, nativeQuery = true)
    List<Object[]> getSentimentTimeline(
            @Param("ticker") String ticker,
            @Param("since")  Instant since,
            @Param("bucket") String bucketInterval
    );

    boolean existsByContentHash(String contentHash);

    void saveAll(List<MentionEvent> batch);
}