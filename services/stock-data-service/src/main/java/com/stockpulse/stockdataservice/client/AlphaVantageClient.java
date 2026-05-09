package com.stockpulse.stockdataservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AlphaVantageClient {

    private final WebClient webClient;

    @Value("${alpha-vantage.api-key}")
    private String apiKey;

    private static final String BASE = "https://www.alphavantage.co/query";

    /**
     * Fetch intraday prices (5min interval) for a ticker.
     */
    public List<PriceBar> getIntradayPrices(String ticker) {
        var url = BASE + "?function=TIME_SERIES_INTRADAY"
                + "&symbol=" + ticker
                + "&interval=5min"
                + "&outputsize=compact"
                + "&apikey=" + apiKey;

        var raw = webClient.get().uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (raw == null) return List.of();

        var series = (Map<String, Map<String, String>>) raw.get("Time Series (5min)");
        if (series == null) return List.of();

        return series.entrySet().stream()
                .map(e -> new PriceBar(
                        e.getKey(),
                        Double.parseDouble(e.getValue().get("1. open")),
                        Double.parseDouble(e.getValue().get("2. high")),
                        Double.parseDouble(e.getValue().get("3. low")),
                        Double.parseDouble(e.getValue().get("4. close")),
                        Long.parseLong(e.getValue().get("5. volume"))
                ))
                .sorted(Comparator.comparing(PriceBar::timestamp))
                .toList();
    }

    /**
     * Batch quotes — single API call with BATCH_STOCK_QUOTES function.
     */
    public Map<String, QuoteDto> getBatchQuotes(List<String> tickers) {
        // Alpha Vantage free tier: one at a time. Premium allows batch.
        // For free tier, limit to top 20 most active tickers.
        var results = new HashMap<String, QuoteDto>();
        tickers.stream().limit(20).forEach(ticker -> {
            try {
                var q = getSingleQuote(ticker);
                if (q != null) results.put(ticker, q);
                Thread.sleep(300); // 5 req/min free tier
            } catch (Exception ignored) {}
        });
        return results;
    }

    private QuoteDto getSingleQuote(String ticker) {
        var url = BASE + "?function=GLOBAL_QUOTE&symbol=" + ticker + "&apikey=" + apiKey;
        var raw = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
        if (raw == null) return null;
        var q = (Map<String, String>) raw.get("Global Quote");
        if (q == null || q.isEmpty()) return null;
        return new QuoteDto(
                ticker,
                Double.parseDouble(q.getOrDefault("05. price", "0")),
                Double.parseDouble(q.getOrDefault("09. change", "0")),
                Double.parseDouble(q.getOrDefault("10. change percent", "0").replace("%", "")),
                Long.parseLong(q.getOrDefault("06. volume", "0"))
        );
    }

    public record PriceBar(String timestamp, double open, double high, double low, double close, long volume) {}
    public record QuoteDto(String ticker, double price, double change, double changePct, long volume) {}
}