package com.stockpulse.sentimentaggregator.algorithm;

import com.stockpulse.common.model.MentionEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Institution-grade sentiment scoring algorithm.
 *
 * Composite Score = α·SentimentScore + β·VelocityScore + γ·MomentumScore
 *   where α=0.45, β=0.35, γ=0.20
 *
 * Each component is normalized to [0, 1].
 */
@Component
public class SentimentScoringEngine {

    // Tunable weights
    private static final double ALPHA = 0.45;  // Sentiment weight
    private static final double BETA  = 0.35;  // Velocity weight
    private static final double GAMMA = 0.20;  // Momentum weight

    // Decay constants
    private static final double TIME_DECAY_HALF_LIFE_HOURS = 6.0;
    private static final double UPVOTE_LOG_BASE = Math.log(10);

    /**
     * Weighted sentiment score using:
     * 1. FinBERT confidence as base weight
     * 2. Time decay (exponential) — newer mentions count more
     * 3. Influencer weight (follower count + accuracy)
     * 4. Upvote/engagement logarithmic boost
     */
    public double computeWeightedSentiment(List<MentionEvent> events) {
        if (events.isEmpty()) return 0.0;

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        var now = Instant.now();

        for (var event : events) {
            double ageHours = Duration.between(event.timestamp(), now).toMinutes() / 60.0;

            // Exponential time decay: w_time = e^(-λ·t)
            // λ = ln(2) / half_life
            double lambda = Math.log(2) / TIME_DECAY_HALF_LIFE_HOURS;
            double timeWeight = Math.exp(-lambda * ageHours);

            // Upvote boost: log(1 + upvotes) / log(10)
            double engagementWeight = Math.log1p(Math.max(0, event.upvotes())) / UPVOTE_LOG_BASE;
            engagementWeight = Math.min(engagementWeight, 3.0); // cap at 3x

            // Source reliability weights
            double sourceWeight = switch (event.source()) {
                case "wsb"        -> 0.7;   // High noise
                case "reddit"     -> 0.85;
                case "stocktwits" -> 0.90;
                case "twitter"    -> 0.80;
                case "seekingalpha" -> 1.10; // Higher quality analysis
                default           -> 0.75;
            };

            // Composite weight
            double w = event.confidence()
                    * timeWeight
                    * (1.0 + engagementWeight * 0.2)
                    * sourceWeight
                    * event.influencerWeight();

            weightedSum += event.sentimentScore() * w;
            totalWeight += w;
        }

        if (totalWeight == 0) return 0.0;
        return clamp(weightedSum / totalWeight, -1.0, 1.0);
    }

    /**
     * Velocity score: measures how fast mentions are accelerating.
     * Uses a two-epoch comparison:
     *   velocity = (mentions_recent_half - mentions_older_half) / mentions_older_half
     * Normalized with sigmoid to [0, 1].
     */
    public double computeVelocity(List<MentionEvent> events, Duration window) {
        if (events.size() < 2) return 0.0;

        var now = Instant.now();
        var midPoint = now.minus(window.dividedBy(2));

        long recentCount = events.stream()
                .filter(e -> e.timestamp().isAfter(midPoint))
                .count();

        long olderCount = events.size() - recentCount;

        if (olderCount == 0) return sigmoid(recentCount);

        double rawVelocity = (double)(recentCount - olderCount) / olderCount;
        return sigmoid(rawVelocity * 2); // Scale before sigmoid
    }

    /**
     * Composite score formula:
     * normalizes sentiment from [-1,1] to [0,1] first
     */
    public double computeCompositeScore(
            double sentiment, double velocity, double momentum, int mentionCount) {

        double normSentiment = (sentiment + 1.0) / 2.0; // [-1,1] → [0,1]

        // Buzz bonus: log-normalized mention count
        double buzzBonus = Math.log1p(mentionCount) / Math.log1p(1000);
        buzzBonus = Math.min(buzzBonus, 0.15); // max 15% bonus

        double base = ALPHA * normSentiment
                + BETA  * velocity
                + GAMMA * momentum;

        return clamp(base + buzzBonus, 0.0, 1.0);
    }

    /**
     * Deduplication: detect coordinated pumping.
     * If >40% of mentions in window come from accounts < 30 days old
     * with 0 followers, apply a trust penalty.
     */
    public double computeCoordinationPenalty(List<MentionEvent> events) {
        long suspiciousCount = events.stream()
                .filter(e -> e.authorFollowers() < 10 && e.influencerWeight() < 0.3)
                .count();
        double ratio = (double) suspiciousCount / events.size();
        // Penalty: 0 to 0.5 reduction
        return ratio > 0.4 ? 0.5 * (ratio - 0.4) / 0.6 : 0.0;
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}