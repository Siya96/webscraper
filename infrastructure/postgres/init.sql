CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE stocks (
    ticker          VARCHAR(10) PRIMARY KEY,
    company_name    VARCHAR(255),
    sector          VARCHAR(100),
    market_cap      BIGINT,
    pe_ratio        DECIMAL(10,2),
    forward_pe      DECIMAL(10,2),
    pb_ratio        DECIMAL(10,2),
    ev_ebitda       DECIMAL(10,2),
    price           DECIMAL(10,4),
    volume_avg      BIGINT,
    last_updated    TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE stock_prices (
    time        TIMESTAMPTZ NOT NULL,
    ticker      VARCHAR(10) NOT NULL,
    open        DECIMAL(10,4),
    high        DECIMAL(10,4),
    low         DECIMAL(10,4),
    close       DECIMAL(10,4),
    volume      BIGINT,
    vwap        DECIMAL(10,4)
);
SELECT create_hypertable('stock_prices', 'time');
CREATE INDEX ON stock_prices (ticker, time DESC);

CREATE TABLE mentions (
    id              BIGSERIAL,
    time            TIMESTAMPTZ NOT NULL,
    ticker          VARCHAR(10),
    source          VARCHAR(50),
    content_hash    VARCHAR(64),
    raw_text        TEXT,
    sentiment_score DECIMAL(5,4),
    sentiment_label VARCHAR(20),
    confidence      DECIMAL(5,4),
    author          VARCHAR(255),
    author_karma    INT,
    author_followers BIGINT,
    influencer_weight DECIMAL(6,4),
    upvotes         INT DEFAULT 0,
    url             TEXT
);
SELECT create_hypertable('mentions', 'time');
CREATE INDEX ON mentions (ticker, time DESC);
CREATE INDEX ON mentions (source, time DESC);
CREATE UNIQUE INDEX ON mentions (content_hash);

CREATE TABLE sentiment_scores (
    time            TIMESTAMPTZ NOT NULL,
    ticker          VARCHAR(10) NOT NULL,
    window_type     VARCHAR(20),
    mention_count   INT,
    weighted_score  DECIMAL(8,6),
    momentum_score  DECIMAL(8,6),
    composite_score DECIMAL(8,6),
    velocity        DECIMAL(8,6),
    PRIMARY KEY (time, ticker, window_type)
);
SELECT create_hypertable('sentiment_scores', 'time');

CREATE TABLE influencers (
    author          VARCHAR(255) PRIMARY KEY,
    platform        VARCHAR(50),
    followers       BIGINT,
    accuracy_score  DECIMAL(5,4),
    weight          DECIMAL(6,4),
    last_updated    TIMESTAMPTZ DEFAULT NOW()
);

CREATE MATERIALIZED VIEW sentiment_1h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    ticker,
    COUNT(*) AS mention_count,
    AVG(sentiment_score * influencer_weight) AS weighted_sentiment,
    SUM(upvotes) AS total_upvotes
FROM mentions
GROUP BY bucket, ticker;

CREATE MATERIALIZED VIEW sentiment_1d
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', time) AS bucket,
    ticker,
    COUNT(*) AS mention_count,
    AVG(sentiment_score * influencer_weight) AS weighted_sentiment
FROM mentions
GROUP BY bucket, ticker;
