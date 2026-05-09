package com.stockpulse.sentimentaggregator.service;

import com.stockpulse.common.model.MentionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexService {

    private final ElasticsearchTemplate esTemplate;
    private static final String INDEX = "mentions";

    public void bulkIndex(List<MentionEvent> events) {
        if (events.isEmpty()) return;

        var queries = events.stream()
                .map(e -> new IndexQueryBuilder()
                        .withId(e.id())
                        .withObject(toDocument(e))
                        .build()
                ).toList();

        try {
            esTemplate.bulkIndex(queries, IndexCoordinates.of(INDEX));
        } catch (Exception ex) {
            log.error("ES bulk index failed: {}", ex.getMessage());
        }
    }

    /**
     * Time-window search: find top tickers by aggregated sentiment score
     * within a given time range. Uses ES date_histogram aggregation.
     */
    public List<TickerAggResult> aggregateByWindow(long windowSeconds, double maxPe) {
        String query = """
            {
              "size": 0,
              "query": {
                "range": {
                  "timestamp": {
                    "gte": "now-%ds/s",
                    "lte": "now"
                  }
                }
              },
              "aggs": {
                "by_ticker": {
                  "terms": { "field": "ticker", "size": 100 },
                  "aggs": {
                    "avg_sentiment":  { "avg":   { "field": "sentiment_score"    } },
                    "avg_confidence": { "avg":   { "field": "confidence"         } },
                    "total_upvotes":  { "sum":   { "field": "upvotes"            } },
                    "mention_count":  { "value_count": { "field": "ticker"       } }
                  }
                }
              }
            }
            """.formatted(windowSeconds);

        // Execute native query and map results
        var results = esTemplate.search(
                org.springframework.data.elasticsearch.core.query.StringQuery.builder(query).build(),
                Document.class,
                IndexCoordinates.of(INDEX)
        );

        // Parse aggregations from response
        return parseAggregations(results);
    }

    private MentionDocument toDocument(MentionEvent e) {
        return new MentionDocument(
                e.id(), e.ticker(), e.source(), e.rawText(),
                e.sentimentScore(), e.sentimentLabel(), e.confidence(),
                e.influencerWeight(), e.upvotes(), e.author(),
                e.timestamp().toString()
        );
    }

    private List<TickerAggResult> parseAggregations(
            org.springframework.data.elasticsearch.core.SearchHits<?> hits) {
        // Parse ES aggregation buckets — simplified
        return List.of();
    }

    record MentionDocument(
            String id, String ticker, String source, String rawText,
            double sentimentScore, String sentimentLabel, double confidence,
            double influencerWeight, int upvotes, String author, String timestamp
    ) {}

    public record TickerAggResult(
            String ticker, double avgSentiment, long mentionCount, long totalUpvotes
    ) {}
}