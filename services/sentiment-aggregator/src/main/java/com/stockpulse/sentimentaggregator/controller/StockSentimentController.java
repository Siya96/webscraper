package com.stockpulse.sentimentaggregator.controller;

import com.stockpulse.common.model.StockRanking;
import com.stockpulse.sentimentaggregator.service.SentimentAggregatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockSentimentController {

    private final SentimentAggregatorService aggregatorService;

    /**
     * GET /api/v1/stocks/rankings
     * Query params:
     *   windowSeconds  - time window (e.g. 3600 = 1h, 86400 = 1d)
     *   limit          - max results (default 20)
     *   maxPe          - P/E filter (default 25)
     *   minMentions    - minimum mentions filter (default 5)
     */
    @GetMapping("/rankings")
    public Mono<ResponseEntity<List<StockRanking>>> getRankings(
            @RequestParam(defaultValue = "3600")  long windowSeconds,
            @RequestParam(defaultValue = "20")    int  limit,
            @RequestParam(defaultValue = "25.0")  double maxPe,
            @RequestParam(defaultValue = "5")     int  minMentions
    ) {
        var window = Duration.ofSeconds(windowSeconds);
        var rankings = aggregatorService.getTopStocks(window, limit, maxPe);
        var filtered = rankings.stream()
                .filter(r -> r.mentionCount() >= minMentions)
                .toList();
        return Mono.just(ResponseEntity.ok(filtered));
    }

    /**
     * GET /api/v1/stocks/{ticker}/sentiment
     * Historical sentiment for a specific stock over a window
     */
    @GetMapping("/{ticker}/sentiment")
    public Mono<ResponseEntity<List<SentimentPoint>>> getTickerSentiment(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "86400") long windowSeconds,
            @RequestParam(defaultValue = "3600")  long bucketSeconds
    ) {
        return aggregatorService
                .getTickerSentimentHistory(ticker, Duration.ofSeconds(windowSeconds), Duration.ofSeconds(bucketSeconds))
                .collectList()
                .map(ResponseEntity::ok);
    }

    /** GET /api/v1/stocks/search?q=AAPL */
    @GetMapping("/search")
    public Mono<ResponseEntity<List<StockRanking>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "86400") long windowSeconds
    ) {
        return aggregatorService.searchStocks(q, Duration.ofSeconds(windowSeconds))
                .collectList()
                .map(ResponseEntity::ok);
    }
}