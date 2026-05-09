package com.stockpulse.sentimentaggregator.repository;

import com.stockpulse.sentimentaggregator.entity.InfluencerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface InfluencerRepository extends JpaRepository<InfluencerEntity, String> {

    Optional<InfluencerEntity> findByAuthorAndPlatform(String author, String platform);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE influencers i
        SET accuracy_score = (
            SELECT COALESCE(
                AVG(CASE
                    WHEN m.sentiment_label = 'POSITIVE' AND sp.close > sp_prev.close THEN 1.0
                    WHEN m.sentiment_label = 'NEGATIVE' AND sp.close < sp_prev.close THEN 1.0
                    ELSE 0.0
                END), 0.5)
            FROM mentions m
            JOIN stock_prices sp      ON sp.ticker = m.ticker
                AND sp.time BETWEEN m.time AND m.time + INTERVAL '5 days'
            JOIN stock_prices sp_prev ON sp_prev.ticker = m.ticker
                AND sp_prev.time = (
                    SELECT MAX(time) FROM stock_prices
                    WHERE ticker = m.ticker AND time < m.time
                )
            WHERE m.author = i.author
              AND m.time >= NOW() - INTERVAL '90 days'
        ),
        last_updated = NOW()
        """, nativeQuery = true)
    void recomputeAccuracyScores();
}