package com.stockpulse.common.model;

import java.time.Instant;

public record StockRanking(
        String ticker,
        String companyName,
        double compositeScore,    // Final ranking score
        double sentimentScore,    // Weighted sentiment -1.0 to 1.0
        double momentumScore,     // Price momentum
        double velocityScore,     // Mention velocity
        double peRatio,
        double forwardPe,
        int mentionCount,
        long windowSeconds,       // Time window in seconds
        Instant calculatedAt
) {}