package com.stockpulse.sentimentaggregator.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockEntity {

    @Id
    @Column(name = "ticker", length = 10)
    private String ticker;

    @Column(name = "company_name")
    private String companyName;

    private String sector;

    @Column(name = "market_cap")
    private Long marketCap;

    @Column(name = "pe_ratio", precision = 10, scale = 2)
    private BigDecimal peRatio = BigDecimal.ZERO;

    @Column(name = "forward_pe", precision = 10, scale = 2)
    private BigDecimal forwardPe = BigDecimal.ZERO;

    @Column(name = "pb_ratio", precision = 10, scale = 2)
    private BigDecimal pbRatio = BigDecimal.ZERO;

    @Column(name = "ev_ebitda", precision = 10, scale = 2)
    private BigDecimal evEbitda = BigDecimal.ZERO;

    @Column(precision = 10, scale = 4)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "volume_avg")
    private Long volumeAvg;

    @Column(name = "last_updated")
    private Instant lastUpdated = Instant.now();

    public StockEntity(String ticker) {
        this.ticker = ticker;
    }

    public double getPeRatio()   { return peRatio  != null ? peRatio.doubleValue()  : 0.0; }
    public double getForwardPe() { return forwardPe != null ? forwardPe.doubleValue() : 0.0; }
    public double getPbRatio()   { return pbRatio   != null ? pbRatio.doubleValue()  : 0.0; }
}