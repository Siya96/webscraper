package com.stockpulse.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record MentionEvent(
        String id,
        String ticker,
        String source,           // reddit, twitter, stocktwits, wsb
        String rawText,
        String contentHash,
        String author,
        long authorFollowers,
        int upvotes,
        double sentimentScore,   // -1.0 to 1.0 from FinBERT
        String sentimentLabel,   // POSITIVE, NEGATIVE, NEUTRAL
        double confidence,
        double influencerWeight,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        String url
) {}