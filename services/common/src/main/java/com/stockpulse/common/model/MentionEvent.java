package com.stockpulse.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record MentionEvent(
    String id, String ticker, String source, String rawText,
    String contentHash, String author, long authorFollowers,
    int upvotes, double sentimentScore, String sentimentLabel,
    double confidence, double influencerWeight,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
    String url
) {}
