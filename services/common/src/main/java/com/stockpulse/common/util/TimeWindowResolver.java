package com.stockpulse.common.util;

import java.time.Duration;
import java.time.Instant;

public final class TimeWindowResolver {
    private TimeWindowResolver() {}

    public static Instant cutoff(long windowSeconds) {
        return Instant.now().minusSeconds(windowSeconds);
    }

    public static String bucketInterval(long windowSeconds) {
        if      (windowSeconds <=  3_600)    return "5 minutes";
        else if (windowSeconds <= 14_400)    return "15 minutes";
        else if (windowSeconds <= 86_400)    return "1 hour";
        else if (windowSeconds <= 604_800)   return "6 hours";
        else if (windowSeconds <= 2_592_000) return "1 day";
        else if (windowSeconds <= 7_776_000) return "3 days";
        else                                 return "1 week";
    }

    public static Duration cacheTtl(long windowSeconds) {
        if      (windowSeconds <=  3_600)  return Duration.ofSeconds(5);
        else if (windowSeconds <= 86_400)  return Duration.ofSeconds(30);
        else if (windowSeconds <= 604_800) return Duration.ofMinutes(5);
        else                               return Duration.ofMinutes(30);
    }
}
