package com.stockpulse.sentimentaggregator.algorithm;

import com.stockpulse.sentimentaggregator.repository.InfluencerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Influencer Weighting System
 *
 * Weight = f(followers, accuracy, platform, account_age, verification)
 *
 * Base weight formula:
 *   w = log10(1 + followers) / log10(MAX_FOLLOWERS) * accuracy * platform_mult * age_mult
 *
 * accuracy = rolling 90-day prediction accuracy vs actual price movement
 *            (1.0 = perfect, 0.5 = random, 0.0 = inverse)
 */
@Component
@RequiredArgsConstructor
public class InfluencerWeightingService {

    private final InfluencerRepository influencerRepo;
    private final ConcurrentHashMap<String, Double> weightCache = new ConcurrentHashMap<>();

    private static final double MAX_FOLLOWERS = 10_000_000.0;

    private static final Map<String, Double> PLATFORM_MULTIPLIERS = Map.of(
            "twitter",      1.0,
            "stocktwits",   0.95,
            "reddit",       0.85,
            "wsb",          0.70,
            "seekingalpha", 1.15,
            "bloomberg",    1.30
    );

    public double getWeight(String author, String platform, long followers) {
        return weightCache.computeIfAbsent(
                author + ":" + platform,
                k -> computeWeight(author, platform, followers)
        );
    }

    private double computeWeight(String author, String platform, long followers) {
        // Fetch stored accuracy (defaults to 0.5 = neutral)
        double accuracy = influencerRepo
                .findByAuthorAndPlatform(author, platform)
                .map(i -> i.getAccuracyScore())
                .orElse(0.5);

        // Follower log-normalization
        double followerScore = followers > 0
                ? Math.log10(1 + followers) / Math.log10(MAX_FOLLOWERS)
                : 0.1; // floor for no-follower accounts

        // Platform trust multiplier
        double platformMult = PLATFORM_MULTIPLIERS.getOrDefault(platform, 0.75);

        // Account newness penalty: loaded from DB, default to 0.8 (slight penalty)
        double ageMult = influencerRepo
                .findByAuthorAndPlatform(author, platform)
                .map(i -> computeAgeMult(i.getAccountAgeDays()))
                .orElse(0.8);

        // Verification bonus
        double verificationMult = influencerRepo
                .findByAuthorAndPlatform(author, platform)
                .map(i -> i.isVerified() ? 1.15 : 1.0)
                .orElse(1.0);

        double weight = followerScore * accuracy * platformMult * ageMult * verificationMult;

        // Normalize: typical range 0.1 - 2.0
        return Math.max(0.1, Math.min(2.0, weight));
    }

    /**
     * Age multiplier: penalize new accounts (potential bots/pumpers).
     * < 30 days: 0.3x, < 90 days: 0.6x, < 365 days: 0.85x, > 365 days: 1.0x
     */
    private double computeAgeMult(int accountAgeDays) {
        if (accountAgeDays < 30)  return 0.30;
        if (accountAgeDays < 90)  return 0.60;
        if (accountAgeDays < 365) return 0.85;
        return 1.0;
    }

    /**
     * Update accuracy scores daily based on whether bullish/bearish calls
     * led to actual price movements within 5 trading days.
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI") // After market close daily
    public void recomputeAccuracyScores() {
        weightCache.clear(); // Invalidate cache
        influencerRepo.recomputeAccuracyScores(); // Native query in repo
    }
}