package com.stockpulse.sentimentaggregator.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "stock_prices")
@Data
public class StockPriceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant time;

    @Column(length = 10, nullable = false)
    private String ticker;

    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private double vwap;
}