package com.stockpulse.sentimentaggregator.repository;

import com.stockpulse.sentimentaggregator.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<StockEntity, String> {

    Optional<StockEntity> findByTicker(String ticker);

    @Query(value = """
        SELECT DISTINCT m.ticker FROM mentions m
        WHERE m.time >= NOW() - INTERVAL '30 days'
        """, nativeQuery = true)
    List<String> findActiveTickers();

    @Query(value = """
        SELECT * FROM stocks
        WHERE pe_ratio > 0 AND pe_ratio <= :maxPe
        ORDER BY pe_ratio ASC
        """, nativeQuery = true)
    List<StockEntity> findUndervaluedStocks(double maxPe);
}