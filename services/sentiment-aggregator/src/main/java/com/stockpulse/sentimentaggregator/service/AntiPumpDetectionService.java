package com.stockpulse.sentimentaggregator.service;

import com.stockpulse.common.model.MentionEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects coordinated pump-and-dump campaigns and bot activity.
 *
 * Signals:
 * 1. Sudden velocity spike (10x normal rate in < 30 min)
 * 2. Cluster of new accounts (< 30 days old)
 * 3. Duplicate/near-duplicate text patterns
 * 4. Single-platform dominance (> 80% from one source)
 * 5. Synchronized posting times (< 5s apart for many accounts)
 */
@Service
public class AntiPumpDetectionService {

    private static final double PUMP_VELOCITY_THRESHOLD   = 10.0;
    private static final double NEW_ACCOUNT_RATIO_LIMIT   = 0.50;
    private static final double SINGLE_SOURCE_RATIO_LIMIT = 0.80;
    private static final int    SYNC_WINDOW_SECONDS       = 5;
    private static final int    SYNC_COUNT_THRESHOLD      = 5;

    public record PumpSignal(
            String ticker,
            boolean isSuspected,
            double penaltyMultiplier,   // 0.0 = fully suppressed, 1.0 = normal
            List<String> reasons
    ) {}

    public PumpSignal analyze(String ticker, List<MentionEvent> events) {
        var reasons = new ArrayList<String>();
        double penalty = 1.0;

        if (events.size() < 10) {
            return new PumpSignal(ticker, false, 1.0, List.of());
        }

        // ── 1. Velocity spike check ───────────────────────────────────
        double velocityRatio = computeVelocityRatio(events);
        if (velocityRatio > PUMP_VELOCITY_THRESHOLD) {
            reasons.add("Velocity spike: %.1fx normal".formatted(velocityRatio));
            penalty *= 0.60;
        }

        // ── 2. New account ratio ──────────────────────────────────────
        long newAccounts = events.stream()
                .filter(e -> e.authorFollowers() < 10 && e.influencerWeight() < 0.4)
                .count();
        double newRatio = (double) newAccounts / events.size();
        if (newRatio > NEW_ACCOUNT_RATIO_LIMIT) {
            reasons.add("%.0f%% low-credibility accounts".formatted(newRatio * 100));
            penalty *= Math.max(0.3, 1.0 - newRatio);
        }

        // ── 3. Single-platform dominance ──────────────────────────────
        var sourceCounts = events.stream()
                .collect(Collectors.groupingBy(MentionEvent::source, Collectors.counting()));
        long maxSourceCount = sourceCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double sourceDominance = (double) maxSourceCount / events.size();
        if (sourceDominance > SINGLE_SOURCE_RATIO_LIMIT) {
            var dominantSource = sourceCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("unknown");
            reasons.add("%.0f%% from single source: %s".formatted(sourceDominance * 100, dominantSource));
            penalty *= 0.80;
        }

        // ── 4. Synchronized posting detection ────────────────────────
        long syncCount = countSynchronizedPosts(events);
        if (syncCount >= SYNC_COUNT_THRESHOLD) {
            reasons.add("%d synchronized posts within %ds".formatted(syncCount, SYNC_WINDOW_SECONDS));
            penalty *= 0.70;
        }

        // ── 5. Text similarity (near-duplicate detection) ─────────────
        double dupeRatio = computeDuplicateRatio(events);
        if (dupeRatio > 0.3) {
            reasons.add("%.0f%% near-duplicate text patterns".formatted(dupeRatio * 100));
            penalty *= Math.max(0.4, 1.0 - dupeRatio);
        }

        boolean suspected = !reasons.isEmpty();
        return new PumpSignal(ticker, suspected, Math.max(0.1, penalty), reasons);
    }

    private double computeVelocityRatio(List<MentionEvent> events) {
        if (events.size() < 4) return 1.0;
        var sorted = events.stream()
                .sorted(Comparator.comparing(MentionEvent::timestamp))
                .toList();

        var totalDuration = Duration.between(
                sorted.get(0).timestamp(), sorted.get(sorted.size() - 1).timestamp()
        );
        if (totalDuration.isZero()) return 1.0;

        // Compare first half vs last 30 minutes
        var thirtyMinAgo = Instant.now().minus(Duration.ofMinutes(30));
        long recentCount = events.stream()
                .filter(e -> e.timestamp().isAfter(thirtyMinAgo))
                .count();
        long olderCount = events.size() - recentCount;

        if (olderCount == 0) return recentCount;
        double rate = (double) recentCount / olderCount;
        // Normalize for time: rate per unit of half the window
        return rate;
    }

    private long countSynchronizedPosts(List<MentionEvent> events) {
        var sorted = events.stream()
                .sorted(Comparator.comparing(MentionEvent::timestamp))
                .toList();

        long maxSync = 0;
        for (int i = 0; i < sorted.size(); i++) {
            var windowEnd = sorted.get(i).timestamp().plusSeconds(SYNC_WINDOW_SECONDS);
            long inWindow = 1;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(j).timestamp().isBefore(windowEnd)) inWindow++;
                else break;
            }
            maxSync = Math.max(maxSync, inWindow);
        }
        return maxSync;
    }

    private double computeDuplicateRatio(List<MentionEvent> events) {
        // Simple n-gram fingerprint comparison
        var fingerprints = events.stream()
                .map(e -> shingleHash(e.rawText()))
                .toList();

        long duplicates = 0;
        for (int i = 0; i < fingerprints.size(); i++) {
            for (int j = i + 1; j < Math.min(fingerprints.size(), i + 20); j++) {
                if (jaccardSimilarity(fingerprints.get(i), fingerprints.get(j)) > 0.7) {
                    duplicates++;
                    break;
                }
            }
        }
        return (double) duplicates / events.size();
    }

    private Set<String> shingleHash(String text) {
        var words  = text.toLowerCase().split("\\s+");
        var shingles = new HashSet<String>();
        for (int i = 0; i < words.length - 2; i++) {
            shingles.add(words[i] + " " + words[i+1] + " " + words[i+2]);
        }
        return shingles;
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        var intersection = new HashSet<>(a);
        intersection.retainAll(b);
        var union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}