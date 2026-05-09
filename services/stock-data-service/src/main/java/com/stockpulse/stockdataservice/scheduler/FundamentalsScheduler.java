package com.stockpulse.stockdataservice.scheduler;

import com.stockpulse.stockdataservice.client.AlphaVantageClient;
import com.stockpulse.stockdataservice.client.FmpClient;
import com.stockpulse.stockdataservice.entity.StockEntity;
import com.stockpulse.stockdataservice.repository.StockEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundamentalsScheduler {

    private final AlphaVantageClient alphaVantageClient;
    private final FmpClient fmpClient;
    private final StockEntityRepository stockRepo;
    private final KafkaTemplate<String, StockEntity> stockProducer;

    // Refresh fundamentals every 6 hours during market hours
    @Scheduled(cron = "0 0 */6 * * MON-FRI")
    public void refreshFundamentals() {
        log.info("Refreshing stock fundamentals...");
        var tickers = getActiveTickers();

        for (var ticker : tickers) {
            try {
                var fundamentals = fmpClient.getFundamentals(ticker);
                var stock = stockRepo.findByTicker(ticker)
                        .orElse(new StockEntity(ticker));

                stock.setPeRatio(fundamentals.peRatio());
                stock.setForwardPe(fundamentals.forwardPe());
                stock.setPbRatio(fundamentals.pbRatio());
                stock.setEvEbitda(fundamentals.evEbitda());
                stock.setMarketCap(fundamentals.marketCap());
                stock.setCompanyName(fundamentals.companyName());
                stock.setSector(fundamentals.sector());
                stockRepo.save(stock);

                // Notify other services
                stockProducer.send("stock-fundamentals", ticker, stock);

                Thread.sleep(200); // Rate limit: 5 req/sec
            } catch (Exception e) {
                log.warn("Failed to refresh {}: {}", ticker, e.getMessage());
            }
        }
        log.info("Fundamentals refresh complete for {} tickers", tickers.size());
    }

    // Refresh prices every minute
    @Scheduled(fixedRate = 60_000)
    public void refreshPrices() {
        var tickers = getActiveTickers();
        // Batch quote fetch
        alphaVantageClient.getBatchQuotes(tickers).forEach((ticker, quote) -> {
            try {
                stockProducer.send("stock-prices", ticker, quote);
            } catch (Exception e) {
                log.warn("Failed to publish price for {}: {}", ticker, e.getMessage());
            }
        });
    }

    private List<String> getActiveTickers() {
        // Return all tickers that have been mentioned in the last 30 days
        return stockRepo.findActiveTickers();
    }
}