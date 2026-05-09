package com.stockpulse.sentimentaggregator.service;

import com.stockpulse.common.model.MentionEvent;
import com.stockpulse.common.model.StockRanking;
import com.stockpulse.sentimentaggregator.algorithm.SentimentScoringEngine;
import com.stockpulse.sentimentaggregator.algorithm.MomentumCalculator;
import com.stockpulse.sentimentaggregator.repository.MentionRepository;
import com.stockpulse.sentimentaggregator.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentimentAggregatorService {

    private final SentimentScoringEngine scoringEngine;
    private final MomentumCalculator momentumCalculator;
    private final MentionRepository mentionRepository;
    private final StockRepository stockRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, StockRanking> rankingProducer;
    private final ElasticsearchIndexService esIndexService;

    // In-memory sliding window buffer (supplemented by Kafka log replay for longer windows)
    private final ConcurrentHashMap<String, Deque<MentionEvent>> realtimeBuffer = new ConcurrentHashMap<>();
    private static final int BUFFER_MAX_SIZE = 10_000;

    @KafkaListener(
            topics = "scored-mentions",
            containerFactory = "mentionListenerFactory",
            groupId = "sentiment-aggregator-group"
    )
    public void consumeScoredMentions(List<MentionEvent> batch, Acknowledgment ack) {
        log.debug("Processing batch of {} mentions", batch.size());

        // 1. Persist to TimescaleDB
        mentionRepository.saveAll(batch);

        // 2. Index to Elasticsearch
        esIndexService.bulkIndex(batch);

        // 3. Update in-memory buffer for real-time window
        batch.forEach(event -> {
            realtimeBuffer.compute(event.ticker(), (k, deque) -> {
                if (deque == null) deque = new ArrayDeque<>();
                deque.addLast(event);
                while (deque.size() > BUFFER_MAX_SIZE) deque.pollFirst();
                return deque;
            });
        });

        // 4. Recompute hot rankings
        recomputeRankings(Duration.ofHours(1));

        ack.acknowledge();
    }

    @Scheduled(fixedRate = 5000)  // Every 5 seconds
    public void recomputeAllWindows() {
        recomputeRankings(Duration.ofHours(1));
        recomputeRankings(Duration.ofHours(4));
        recomputeRankings(Duration.ofDays(1));
    }

    public List<StockRanking> getTopStocks(Duration window, int limit, double maxPe) {
        long windowSeconds = window.getSeconds();
        String cacheKey = "rankings:" + windowSeconds + ":" + limit + ":" + maxPe;

        // Check Redis cache first
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .map(json -> parseRankings(json))
                .switchIfEmpty(
                        computeRankingsFromDB(window, limit, maxPe)
                                .doOnNext(rankings -> cacheRankings(cacheKey, rankings, window))
                )
                .block();
    }

    private void recomputeRankings(Duration window) {
        var cutoff = Instant.now().minus(window);
        var allTickers = new HashSet<String>();

        // Collect tickers from buffer
        realtimeBuffer.forEach((ticker, events) -> {
            boolean hasRecent = events.stream()
                    .anyMatch(e -> e.timestamp().isAfter(cutoff));
            if (hasRecent) allTickers.add(ticker);
        });

        var rankings = new ArrayList<StockRanking>();

        for (var ticker : allTickers) {
            var events = getEventsInWindow(ticker, cutoff);
            if (events.isEmpty()) continue;

            var stock = stockRepository.findByTicker(ticker).orElse(null);
            if (stock == null) continue;

            // Only include undervalued stocks (P/E filter)
            if (stock.getPeRatio() <= 0 || stock.getPeRatio() > 25.0) continue;

            double sentiment = scoringEngine.computeWeightedSentiment(events);
            double velocity  = scoringEngine.computeVelocity(events, window);
            double momentum  = momentumCalculator.computeMomentumScore(ticker, window);
            double composite = scoringEngine.computeCompositeScore(sentiment, velocity, momentum, events.size());

            rankings.add(new StockRanking(
                    ticker,
                    stock.getCompanyName(),
                    composite,
                    sentiment,
                    momentum,
                    velocity,
                    stock.getPeRatio(),
                    stock.getForwardPe(),
                    events.size(),
                    window.getSeconds(),
                    Instant.now()
            ));
        }

        // Sort by composite score, take top 50
        rankings.sort(Comparator.comparingDouble(StockRanking::compositeScore).reversed());
        var top = rankings.stream().limit(50).toList();

        // Push to Redis + Kafka
        cacheTopRankings(window.getSeconds(), top);
        top.forEach(r -> rankingProducer.send("live-rankings", r.ticker(), r));
    }

    private List<MentionEvent> getEventsInWindow(String ticker, Instant cutoff) {
        var deque = realtimeBuffer.get(ticker);
        if (deque == null) return List.of();
        return deque.stream()
                .filter(e -> e.timestamp().isAfter(cutoff))
                .toList();
    }

    private void cacheTopRankings(long windowSeconds, List<StockRanking> rankings) {
        var key = "top-rankings:" + windowSeconds;
        // Serialize and store with TTL slightly less than recompute interval
        redisTemplate.opsForValue()
                .set(key, serializeRankings(rankings), Duration.ofSeconds(10))
                .subscribe();
    }
}