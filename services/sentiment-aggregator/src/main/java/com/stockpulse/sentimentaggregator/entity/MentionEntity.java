package com.stockpulse.sentimentaggregator.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "mentions")
@Data
public class MentionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant time;

    @Column(length = 10)
    private String ticker;

    @Column(length = 50)
    private String source;

    @Column(name = "content_hash", length = 64, unique = true)
    private String contentHash;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "sentiment_score", precision = 5, scale = 4)
    private double sentimentScore;

    @Column(name = "sentiment_label", length = 20)
    private String sentimentLabel;

    private double confidence;
    private String author;

    @Column(name = "author_karma")
    private int authorKarma;

    @Column(name = "author_followers")
    private long authorFollowers;

    @Column(name = "influencer_weight", precision = 6, scale = 4)
    private double influencerWeight;

    private int upvotes;
    private String url;
}