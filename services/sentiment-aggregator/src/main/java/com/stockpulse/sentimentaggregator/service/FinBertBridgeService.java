package com.stockpulse.sentimentaggregator.service;

import com.stockpulse.common.model.MentionEvent;
import com.stockpulse.sentimentaggregator.algorithm.InfluencerWeightingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinBertBridgeService {

    private final WebClient finBertClient;
    private final KafkaTemplate<String, MentionEvent> mentionProducer;
    private final InfluencerWeightingService influencerWeighting;

    @Value("${finbert.batch-size:32}")
    private int batchSize;

    @Value("${finbert.timeout-seconds:10}")
    private int timeoutSeconds;

    /**
     * Consumes raw (unscored) mentions from scrapers,
     * enriches with FinBERT sentiment + influencer weights,
     * publishes to scored-mentions topic.
     */
    @KafkaListener(
            topics = "raw-mentions",
            containerFactory = "mentionListenerFactory",
            groupId = "finbert-bridge-group"
    )
    public void processBatch(List<MentionEvent> batch, Acknowledgment ack) {
        log.debug("Bridge: processing {} raw mentions", batch.size());

        // Split into sub-batches of `batchSize`
        var scored = new ArrayList<MentionEvent>(batch.size());

        for (int i = 0; i < batch.size(); i += batchSize) {
            var subBatch = batch.subList(i, Math.min(i + batchSize, batch.size()));
            try {
                var enriched = callFinBert(subBatch);
                scored.addAll(enriched);
            } catch (Exception e) {
                log.error("FinBERT call failed for sub-batch, using neutral fallback: {}", e.getMessage());
                // Fallback: neutral score so mention isn't lost
                subBatch.forEach(m -> scored.add(withNeutralSentiment(m)));
            }
        }

        // Publish scored mentions
        scored.forEach(m -> mentionProducer.send("scored-mentions", m.ticker(), m));

        ack.acknowledge();
        log.debug("Bridge: published {} scored mentions", scored.size());
    }

    private List<MentionEvent> callFinBert(List<MentionEvent> batch) {
        var request = Map.of("mentions", batch.stream().map(m -> Map.of(
                "id",               m.id(),
                "ticker",           m.ticker(),
                "text",             m.rawText(),
                "source",           m.source(),
                "author",           m.author(),
                "author_followers", m.authorFollowers(),
                "upvotes",          m.upvotes(),
                "timestamp",        m.timestamp().toString()
        )).toList());

        var response = finBertClient.post()
                .uri("/score/batch")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FinBertBatchResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        if (response == null || response.results() == null) return List.of();

        // Merge FinBERT scores with influencer weights
        var scoreMap = new HashMap<String, FinBertResult>();
        response.results().forEach(r -> scoreMap.put(r.id(), r));

        return batch.stream().map(m -> {
            var score = scoreMap.get(m.id());
            if (score == null) return withNeutralSentiment(m);

            double influencerWeight = influencerWeighting.getWeight(
                    m.author(), m.source(), m.authorFollowers()
            );

            return new MentionEvent(
                    m.id(),
                    m.ticker(),
                    m.source(),
                    m.rawText(),
                    m.contentHash(),
                    m.author(),
                    m.authorFollowers(),
                    m.upvotes(),
                    score.sentimentScore(),
                    score.sentimentLabel(),
                    score.confidence(),
                    influencerWeight,
                    m.timestamp(),
                    m.url()
            );
        }).toList();
    }

    private MentionEvent withNeutralSentiment(MentionEvent m) {
        return new MentionEvent(
                m.id(), m.ticker(), m.source(), m.rawText(), m.contentHash(),
                m.author(), m.authorFollowers(), m.upvotes(),
                0.0, "NEUTRAL", 0.5,
                influencerWeighting.getWeight(m.author(), m.source(), m.authorFollowers()),
                m.timestamp(), m.url()
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    record FinBertBatchResponse(List<FinBertResult> results, int processed, int failed) {}

    record FinBertResult(
            String id,
            String ticker,
            double sentimentScore,
            String sentimentLabel,
            double confidence
    ) {}
}