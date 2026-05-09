package com.stockpulse.stockdataservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class FmpClient {

    private final WebClient webClient;

    @Value("${fmp.api-key}")
    private String apiKey;

    private static final String BASE = "https://financialmodelingprep.com/api/v3";

    public FundamentalsDto getFundamentals(String ticker) {
        var url = BASE + "/profile/" + ticker + "?apikey=" + apiKey;
        var result = webClient.get().uri(url)
                .retrieve()
                .bodyToFlux(FmpProfileResponse.class)
                .blockFirst();

        if (result == null) throw new RuntimeException("No data for " + ticker);

        return new FundamentalsDto(
                result.symbol(),
                result.companyName(),
                result.pe() != null ? result.pe() : -1.0,
                result.price() / Math.max(1, result.eps()),  // forward P/E approx
                result.priceToBookRatio() != null ? result.priceToBookRatio() : -1.0,
                result.enterpriseValueMultiple() != null ? result.enterpriseValueMultiple() : -1.0,
                result.mktCap() != null ? result.mktCap().longValue() : 0L,
                result.sector()
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    record FmpProfileResponse(
            String symbol, String companyName,
            Double pe, double price, double eps,
            Double priceToBookRatio, Double enterpriseValueMultiple,
            Double mktCap, String sector
    ) {}

    public record FundamentalsDto(
            String ticker, String companyName,
            double peRatio, double forwardPe,
            double pbRatio, double evEbitda,
            long marketCap, String sector
    ) {}
}