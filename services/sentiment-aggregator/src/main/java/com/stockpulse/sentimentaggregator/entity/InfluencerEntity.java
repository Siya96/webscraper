package com.stockpulse.sentimentaggregator.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "influencers")
@Data
public class InfluencerEntity {

    @Id
    private String author;

    private String platform;
    private long followers;

    @Column(name = "accuracy_score")
    private double accuracyScore = 0.5;

    private double weight = 1.0;

    @Column(name = "account_age_days")
    private int accountAgeDays = 365;

    @Column(name = "is_verified")
    private boolean verified = false;

    @Column(name = "last_updated")
    private Instant lastUpdated = Instant.now();
}