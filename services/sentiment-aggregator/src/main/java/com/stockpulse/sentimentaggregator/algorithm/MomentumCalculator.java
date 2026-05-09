package com.stockpulse.sentimentaggregator.algorithm;

import com.stockpulse.sentimentaggregator.repository.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.util.*;

/**
 * Stock Momentum Detection — multi-factor model using TA4J.
 *
 * Factors:
 * 1. RSI (14) — momentum oscillator
 * 2. MACD Signal crossover
 * 3. Bollinger Band position
 * 4. ADX (trend strength)
 * 5. Volume momentum (OBV)
 * 6. Rate of Change (ROC)
 *
 * Final momentum score: weighted combination normalized [0, 1]
 */
@Component
@RequiredArgsConstructor
public class MomentumCalculator {

    private final StockPriceRepository priceRepo;

    public double computeMomentumScore(String ticker, Duration window) {
        var prices = priceRepo.findByTickerInWindow(ticker, window.getSeconds());
        if (prices.size() < 20) return 0.5; // insufficient data

        var series = buildBarSeries(prices, ticker);
        var close  = new ClosePriceIndicator(series);

        // ── RSI ──────────────────────────────────────────────────────
        var rsi = new RSIIndicator(close, 14);
        double rsiVal = rsi.getValue(series.getEndIndex()).doubleValue();
        // RSI 30-70 normal; >60 = bullish momentum
        double rsiScore = rsiVal > 50 ? (rsiVal - 50) / 50.0 : 0.0;

        // ── MACD ─────────────────────────────────────────────────────
        var macd        = new MACDIndicator(close, 12, 26);
        var signalLine  = new EMAIndicator(macd, 9);
        int endIdx      = series.getEndIndex();
        double macdVal  = macd.getValue(endIdx).doubleValue();
        double signalVal = signalLine.getValue(endIdx).doubleValue();
        // Bullish if MACD above signal and positive
        double macdScore = (macdVal > signalVal && macdVal > 0) ? 1.0 :
                (macdVal > signalVal)                  ? 0.5 : 0.0;

        // ── Bollinger Bands ───────────────────────────────────────────
        var bbMiddle = new SMAIndicator(close, 20);
        var bbDev    = new StandardDeviationIndicator(close, 20);
        var bbUpper  = new BollingerBandsUpperIndicator(bbMiddle, bbDev);
        var bbLower  = new BollingerBandsLowerIndicator(bbMiddle, bbDev);
        double closeVal  = close.getValue(endIdx).doubleValue();
        double upperVal  = bbUpper.getValue(endIdx).doubleValue();
        double lowerVal  = bbLower.getValue(endIdx).doubleValue();
        double bbRange   = upperVal - lowerVal;
        // Position in band: 0 = at lower, 1 = at upper
        double bbScore   = bbRange > 0 ? (closeVal - lowerVal) / bbRange : 0.5;

        // ── ADX (trend strength) ──────────────────────────────────────
        var adx    = new ADXIndicator(series, 14);
        var plusDI = new PlusDIIndicator(series, 14);
        var minDI  = new MinusDIIndicator(series, 14);
        double adxVal   = adx.getValue(endIdx).doubleValue();
        double plusDIVal = plusDI.getValue(endIdx).doubleValue();
        double minDIVal  = minDI.getValue(endIdx).doubleValue();
        // Trending up: ADX>25 and +DI>-DI
        double adxScore = (adxVal > 25 && plusDIVal > minDIVal) ? Math.min(adxVal / 60.0, 1.0) : 0.0;

        // ── ROC (Rate of Change) ──────────────────────────────────────
        var roc = new ROCIndicator(close, 10);
        double rocVal   = roc.getValue(endIdx).doubleValue();
        // Normalize: >5% ROC = fully bullish
        double rocScore = clamp((rocVal + 5.0) / 10.0, 0.0, 1.0);

        // ── Weighted Composite ────────────────────────────────────────
        double composite = 0.25 * rsiScore
                + 0.25 * macdScore
                + 0.15 * bbScore
                + 0.20 * adxScore
                + 0.15 * rocScore;

        return clamp(composite, 0.0, 1.0);
    }

    private BarSeries buildBarSeries(List<PricePoint> prices, String ticker) {
        var series = new BaseBarSeriesBuilder().withName(ticker).build();
        for (var p : prices) {
            series.addBar(
                    Duration.ofMinutes(5),
                    p.time(),
                    DecimalNum.valueOf(p.open()),
                    DecimalNum.valueOf(p.high()),
                    DecimalNum.valueOf(p.low()),
                    DecimalNum.valueOf(p.close()),
                    DecimalNum.valueOf(p.volume())
            );
        }
        return series;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}