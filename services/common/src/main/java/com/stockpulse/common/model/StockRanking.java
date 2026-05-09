package com.stockpulse.common.model;

import java.time.Instant;

public record StockRanking(
    String ticker, String companyName,
    double compositeScore, double sentimentScore,
    double momentumScore, double velocityScore,
    double peRatio, double forwardPe,
    int mentionCount, long windowSeconds,
    Instant calculatedAt
) {}
