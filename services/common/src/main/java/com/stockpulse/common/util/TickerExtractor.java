package com.stockpulse.common.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts stock tickers from free text.
 * Uses a curated blocklist of common English words
 * that match the [A-Z]{1,5} pattern.
 */
public final class TickerExtractor {

    private TickerExtractor() {}

    private static final Pattern TICKER_PATTERN =
            Pattern.compile("\\$([A-Z]{1,5})|\\b([A-Z]{2,5})\\b");

    // Common English uppercase words that are NOT tickers
    private static final Set<String> BLOCKLIST = Set.of(
            "I", "A", "AN", "THE", "IN", "ON", "AT", "OR", "AND", "BUT",
            "FOR", "NOT", "BY", "UP", "TO", "DO", "GO", "IF", "MY", "IT",
            "IS", "AM", "ARE", "WAS", "BE", "AS", "OF", "SO", "NO",
            "CEO", "CFO", "COO", "CTO", "IPO", "ETF", "EPS", "PE", "DD",
            "OP", "US", "UK", "EU", "USA", "GDP", "FED", "SEC", "IMF",
            "AI", "ML", "DM", "PM", "AM", "TD", "TV", "OK", "LOL",
            "IMO", "IMHO", "YOLO", "WSB", "RH", "ATH", "ATL", "YOY",
            "QOQ", "EV", "SaaS", "SAAS", "BTC", "ETH", "DOGE",
            "COVID", "FDA", "WHO"
    );

    public static Set<String> extract(String text) {
        var found   = new HashSet<String>();
        var matcher = TICKER_PATTERN.matcher(text);

        while (matcher.find()) {
            // Group 1: $TICKER format (most reliable)
            var dollarTicker = matcher.group(1);
            if (dollarTicker != null) {
                found.add(dollarTicker);
                continue;
            }
            // Group 2: bare uppercase word
            var bareTicker = matcher.group(2);
            if (bareTicker != null && !BLOCKLIST.contains(bareTicker)) {
                found.add(bareTicker);
            }
        }
        return found;
    }
}