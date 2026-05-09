package com.stockpulse.sentimentaggregator.repository;

import com.stockpulse.sentimentaggregator.entity.StockPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPriceEntity, Long> {

    @Query(value = """
        SELECT * FROM stock_prices
        WHERE ticker = :ticker
          AND time >= NOW() - CAST(:windowSeconds || ' seconds' AS INTERVAL)
        ORDER BY time ASC
        """, nativeQuery = true)
    List<StockPriceEntity> findByTickerInWindow(
            @Param("ticker")        String ticker,
            @Param("windowSeconds") long windowSeconds
    );
}