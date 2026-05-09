package com.stockpulse.common.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Resolves a seconds-based time window to:
 * - An Instant cutoff (for DB queries)
 * - A bucket interval string (for TimescaleDB time_bucket)
 * - A cache TTL
 */
public final class TimeWindowResolver {

    private TimeWindowResolver() {}

    public static Instant cutoff(long windowSeconds) {
        return Instant.now().minusSeconds(windowSeconds);
    }

    /**
     * Returns an appropriate TimescaleDB bucket interval for a given window.
     * Keeps result sets manageable (~24-100 data points per chart).
     */
    public static String bucketInterval(long windowSeconds) {
        if      (windowSeconds <=  3_600)   return "5 minutes";   // 1h  → 12 pts
        else if (windowSeconds <= 14_400)   return "15 minutes";  // 4h  → 16 pts
        else if (windowSeconds <= 86_400)   return "1 hour";      // 1d  → 24 pts
        else if (windowSeconds <= 604_800)  return "6 hours";     // 7d  → 28 pts
        else if (windowSeconds <= 2_592_000)return "1 day";       // 30d → 30 pts
        else if (windowSeconds <= 7_776_000)return "3 days";      // 90d → 30 pts
        else                                return "1 week";      // 350d→ 50 pts
    }

    /**
     * Cache TTL: shorter windows expire faster so they stay fresh.
     */
    public static Duration cacheTtl(long windowSeconds) {
        if      (windowSeconds <=  3_600)  return Duration.ofSeconds(5);
        else if (windowSeconds <= 86_400)  return Duration.ofSeconds(30);
        else if (windowSeconds <= 604_800) return Duration.ofMinutes(5);
        else                               return Duration.ofMinutes(30);
    }
}