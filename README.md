# StockPulse

StockPulse is a **real-time stock sentiment analysis and ranking platform**. Its core purpose is to continuously scrape financial social media and forums for stock ticker mentions, run each piece of text through an AI sentiment model, aggregate and rank tickers by their combined sentiment signal, and stream live rankings to a mobile app so traders can spot the most-discussed and most bullish/bearish stocks in near real time.

The system is fully containerised as a microservices architecture orchestrated with Docker Compose. All services communicate asynchronously through Apache Kafka. There is no monolith — every concern (scraping, scoring, aggregating, serving, displaying) lives in its own service.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Data Flow — End to End](#data-flow--end-to-end)
- [Services](#services)
  - [Scraper Cluster](#1-scraper-cluster-servicescraper-cluster)
  - [FinBERT Service](#2-finbert-service-servicesfinbert-service)
  - [Sentiment Aggregator](#3-sentiment-aggregator-servicessentiment-aggregator)
  - [Stock Data Service](#4-stock-data-service-servicesstock-data-service)
  - [API Gateway](#5-api-gateway-servicesapi-gateway)
  - [WebSocket Service](#6-websocket-service-serviceswebsocket-service)
  - [Mobile App](#7-mobile-app-servicesmobile-app)
- [Infrastructure](#infrastructure)
  - [Kafka Topics](#kafka-topics)
  - [Elasticsearch Index](#elasticsearch-index)
  - [Redis](#redis)
  - [PostgreSQL / TimescaleDB](#postgresql--timescaledb)
- [Port Map](#port-map)
- [Environment Variables](#environment-variables)
- [Tech Stack Summary](#tech-stack-summary)
- [Quick Start](#quick-start)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  Social Media Sources                                               │
│  Twitter/X · Reddit WSB · SeekingAlpha · Yahoo Finance Forums      │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Playwright stealth scraping (4 replicas)
                            ▼
                  ┌─────────────────┐
                  │ Scraper Cluster │  Node.js
                  │ (x4 replicas)   │
                  └────────┬────────┘
                           │ Kafka: raw-mentions (12 partitions)
                           ▼
                  ┌─────────────────┐
                  │ FinBERT Service │  Python / FastAPI / PyTorch
                  │ (port 5000)     │  ← called inline by scrapers
                  └────────┬────────┘
                           │ Kafka: scored-mentions (12 partitions)
                           ▼
               ┌───────────────────────┐
               │ Sentiment Aggregator  │  Java / Spring Boot
               │ (port 8082)           │  ZGC GC, -Xmx2g
               └──┬──────────┬─────────┘
                  │          │
           ES index      Kafka: live-rankings (6 partitions, compacted)
                  │          │
                  ▼          ▼
         ┌──────────────┐  ┌──────────────────┐
         │Elasticsearch │  │ WebSocket Service│  Java / Spring Boot
         │  (port 9200) │  │ (port 8085)      │
         └──────────────┘  └────────┬─────────┘
                                    │ ws://host:8085/ws/rankings
                                    │
              ┌─────────────────────┼──────────────────────┐
              │                     ▼                      │
              │           ┌─────────────────┐              │
              │           │   Mobile App    │ React Native  │
              │           │   (Expo 51)     │ / TypeScript  │
              │           └────────┬────────┘              │
              │                    │ REST polls             │
              │                    ▼                        │
              │           ┌─────────────────┐              │
              │           │   API Gateway   │ Java / Spring │
              │           │   (port 8080)   │ Boot          │
              │           └─────────────────┘              │
              │                                             │
              │  ┌──────────────────────────────────────┐  │
              │  │ Stock Data Service  (port 8083)       │  │
              │  │ Alpha Vantage + FMP → stock-prices    │  │
              │  │                    → stock-fundamentals│  │
              │  └──────────────────────────────────────┘  │
              └─────────────────────────────────────────────┘
```

All services run on a single Docker bridge network (`stockpulse-net`). Internal service-to-service communication uses the Docker service DNS names (e.g., `kafka:29092`, `finbert-service:5000`).

---

## Data Flow — End to End

1. **Scraping** — Four replicas of the scraper-cluster run concurrently. Each replica is assigned a scraper target via the `SCRAPER` environment variable (`reddit`, `twitter`, `stocktwits`, `wsb`, `seekingalpha`). Scrapers use Playwright with the `playwright-extra` stealth plugin and `puppeteer-extra-plugin-stealth` to evade bot detection. Browser fingerprints are randomised per session using the `fingerprint-generator` and `user-agents` libraries (`src/antibot/fingerprint.js`). Optional HTTP proxy rotation is supported via the `PROXY_LIST` env var. When a post or comment mentioning a stock ticker is found, it is published to the `raw-mentions` Kafka topic.

2. **Sentiment Scoring** — The scrapers call the FinBERT service directly over HTTP (`FINBERT_URL`) before (or after) publishing. The FinBERT service receives raw text and returns a `sentiment_label` (`positive`, `negative`, `neutral`) plus a float `sentiment_score` (confidence). FinBERT is a finance-domain fine-tuned BERT model loaded via Hugging Face `transformers`. The scored payload is then published to the `scored-mentions` Kafka topic.

3. **Aggregation** — The Sentiment Aggregator service consumes from `scored-mentions`. For each scored mention it updates a per-ticker composite score in Elasticsearch. The composite score weights the raw sentiment by `upvotes`, `influencer_weight` (author follower count proxy), and recency. Aggregated results are also written to PostgreSQL (TimescaleDB) for historical time-series queries. Updated ticker rankings are published to the `live-rankings` Kafka topic (compacted — only the latest state per ticker key is retained).

4. **Live Price & Fundamental Data** — The Stock Data Service polls external financial APIs (Alpha Vantage for price data, Financial Modeling Prep for fundamentals) and publishes to `stock-prices` and `stock-fundamentals` Kafka topics. These enrich the API gateway's responses and can be joined with sentiment scores.

5. **Serving** — The API Gateway (port 8080) exposes REST endpoints consumed by the mobile app. It reads from Elasticsearch and Redis (for caching). The WebSocket Service (port 8085) consumes `live-rankings` and pushes updates to connected mobile clients over a persistent WebSocket connection at `ws://host:8085/ws/rankings`.

6. **Mobile Display** — The React Native app connects to both the REST API and the WebSocket endpoint. The main tab (index) shows a live-ranked feed of stocks by composite sentiment score. The search tab allows querying individual tickers. The settings tab controls app preferences. A `ConnectionStatus` component tracks whether the WebSocket is live. TanStack Query manages REST data fetching and caching; Zustand manages local state; Victory Native and react-native-chart-kit render sentiment trend charts; Shopify FlashList renders the high-performance scrollable ticker list.

---

## Services

### 1. Scraper Cluster (`services/scraper-cluster`)

**Runtime:** Node.js (ESM modules, `"type": "module"`)  
**Container:** `mcr.microsoft.com/playwright:v1.44.0-jammy` base image — includes a full Chromium browser  
**Replicas:** 4 (set in `docker-compose.yml` under `deploy.replicas`)

**Scrapers implemented:**
| Script start command | Source |
|---|---|
| `SCRAPER=reddit` | Reddit (via official Reddit API — requires `REDDIT_CLIENT_ID` + `REDDIT_SECRET`) |
| `SCRAPER=twitter` | Twitter/X (`src/scrapers/twitter.scraper.js`) |
| `SCRAPER=stocktwits` | StockTwits |
| `SCRAPER=wsb` | Reddit r/wallstreetbets (`src/scrapers/wsb.scraper.js`) |
| `SCRAPER=seekingalpha` | SeekingAlpha (`src/scrapers/seekingalpha.scraper.js`) |

**Key dependencies:**
- `playwright` ^1.44.0 — headless Chromium automation
- `playwright-extra` ^4.3.6 — plugin middleware for Playwright
- `puppeteer-extra-plugin-stealth` ^2.11.2 — patches browser fingerprint to appear human
- `fingerprint-generator` ^2.1.42 — generates realistic browser fingerprints
- `user-agents` ^1.1.228 — random realistic user-agent strings
- `kafkajs` ^2.2.4 — Kafka producer client
- `axios` ^1.7.2 — HTTP client (used to call FinBERT service)
- `p-queue` ^8.0.1 — concurrency-limited async queue for scraping
- `winston` ^3.13.0 — structured logging (`src/utils/logger.js`)
- `dotenv` ^16.4.5 — environment variable loading

**Anti-bot strategy:** Each scraping session generates a new randomised browser fingerprint (`src/antibot/fingerprint.js`) combining a fake user-agent, canvas fingerprint, WebGL renderer string, and screen resolution. Combined with the stealth plugin this makes automated traffic appear as organic browser sessions.

**Environment variables:**
- `KAFKA_BROKERS` — e.g. `kafka:29092`
- `SCRAPER` — which scraper to run
- `FINBERT_URL` — URL of FinBERT service, e.g. `http://finbert-service:5000`
- `PROXY_LIST` — comma-separated HTTP proxy URLs (optional)
- `REDDIT_CLIENT_ID` / `REDDIT_SECRET` — Reddit OAuth credentials

---

### 2. FinBERT Service (`services/finbert-service`)

**Runtime:** Python 3.11  
**Framework:** FastAPI + Uvicorn (uvloop event loop, 2 workers)  
**Port:** 5000  
**Container:** `python:3.11-slim`

**What it does:** Receives raw social media text (single string), tokenises it with the FinBERT tokeniser, runs a forward pass through the FinBERT model, and returns a structured JSON response containing:
- `sentiment_label`: `"positive"` | `"negative"` | `"neutral"`
- `sentiment_score`: float confidence 0–1

**AI model:** FinBERT — a BERT model (`bert-base-uncased` architecture) fine-tuned on financial text (analyst reports, earnings calls, financial news). It was shown to outperform general-purpose BERT on finance sentiment tasks. Loaded via Hugging Face `transformers` library with PyTorch as the backend.

**Model caching:** The model is downloaded to `/models` inside the container, which is mapped to a local `./models` volume in docker-compose so the model is not re-downloaded on container restart.

**Key dependencies:**
- `fastapi` 0.111.0
- `uvicorn[standard]` 0.30.0 (includes uvloop + httptools for performance)
- `transformers` 4.41.2 — Hugging Face model loading and tokenisation
- `torch` 2.3.0 — PyTorch inference engine
- `numpy` 1.26.4
- `pydantic` 2.7.2 — request/response validation
- `aiohttp` 3.9.5 — async HTTP
- `prometheus-client` 0.20.0 — exposes `/metrics` endpoint for Prometheus scraping
- `pytest` 8.2.0 / `httpx` 0.27.0 — test suite (`tests/test_scoring.py`)

---

### 3. Sentiment Aggregator (`services/sentiment-aggregator`)

**Runtime:** Java 25 (preview features enabled)  
**Framework:** Spring Boot  
**Port:** 8082  
**JVM flags:** `-Xms512m -Xmx2g -XX:+UseZGC` — ZGC (Z Garbage Collector) chosen for low-latency GC pauses suitable for streaming workloads  
**Container:** `eclipse-temurin:25-jre-alpine`

**What it does:**
- Consumes messages from the `scored-mentions` Kafka topic
- Calculates a **composite score** per ticker by weighting `sentiment_score` with `upvotes`, `influencer_weight`, and time decay
- Writes individual scored mentions to the Elasticsearch index with all enrichment fields
- Writes time-series records to PostgreSQL (TimescaleDB) for historical analysis
- Publishes the updated ticker ranking to the `live-rankings` Kafka topic (used by the WebSocket service for push delivery)
- Uses Redis for hot-path caching of intermediate aggregation state

**Dependencies:** Kafka (scored-mentions consumer, live-rankings producer), Redis, Elasticsearch, PostgreSQL

---

### 4. Stock Data Service (`services/stock-data-service`)

**Runtime:** Java 25  
**Framework:** Spring Boot  
**Port:** 8083  
**Container:** `eclipse-temurin:25-jre-alpine`

**What it does:** Polls external financial market data APIs and feeds the results into Kafka:
- **Alpha Vantage** (`ALPHA_VANTAGE_KEY`) — real-time and historical stock price data → published to `stock-prices` topic
- **Financial Modeling Prep / FMP** (`FMP_API_KEY`) — company fundamentals (P/E ratio, market cap, revenue, etc.) → published to `stock-fundamentals` topic

This service decouples market data ingestion from the sentiment pipeline. The API Gateway can join sentiment scores with live price data to give the mobile app a combined view.

---

### 5. API Gateway (`services/api-gateway`)

**Runtime:** Java 25  
**Framework:** Spring Boot  
**Port:** 8080  
**Container:** `eclipse-temurin:25-jre-alpine`

**What it does:** Serves as the single REST entry point for the mobile app. Reads aggregated sentiment rankings from Elasticsearch (via the aggregator's writes), caches hot responses in Redis, and can join in stock price/fundamental data from PostgreSQL. Handles CORS and request routing for all mobile REST calls.

**Depends on:** Kafka, Redis, PostgreSQL

**Mobile app base URL:** `EXPO_PUBLIC_API_URL=http://localhost:8080`

---

### 6. WebSocket Service (`services/websocket-service`)

**Runtime:** Java 25  
**Framework:** Spring Boot  
**Port:** 8085  
**WebSocket endpoint:** `ws://host:8085/ws/rankings`  
**Container:** `eclipse-temurin:25-jre-alpine`

**What it does:** Consumes the `live-rankings` Kafka topic (compacted — one record per ticker, latest value) and broadcasts updated rankings to all connected WebSocket clients in real time. The mobile app maintains a persistent WebSocket connection and the `ConnectionStatus` component reflects its state. When a ticker's composite score changes, connected clients receive a push update without polling.

**Depends on:** Kafka, Redis

---

### 7. Mobile App (`services/mobile-app`)

**Runtime:** React Native 0.74 / Expo SDK 51  
**Language:** TypeScript 5.3  
**Router:** expo-router 3.5 (file-system routing)  
**UI theme:** Dark (`"userInterfaceStyle": "dark"`, background `#0f0c29` deep navy/purple)  
**Orientation:** Portrait only  
**Bundle IDs:** iOS `com.stockpulse.app` / Android `com.stockpulse.app`

**Screens (tabs):**
| File | Tab | Purpose |
|---|---|---|
| `app/(tabs)/index.tsx` | Home | Live-ranked feed of stocks by composite sentiment score. FlashList for performance. Victory Native charts for sentiment trend. |
| `app/(tabs)/search.tsx` | Search | Search for a specific ticker and view its sentiment history. |
| `app/(tabs)/settings.tsx` | Settings | User preferences (refresh interval, notification thresholds, etc.) |

**Key components:**
- `src/components/ConnectionStatus.tsx` — displays WebSocket connection health (connected / disconnected / reconnecting) using `@react-native-community/netinfo`

**Key libraries:**
| Library | Version | Purpose |
|---|---|---|
| `expo-router` | ^3.5.0 | File-system navigation |
| `@tanstack/react-query` | ^5.40.0 | Server state, REST data fetching, cache invalidation |
| `zustand` | ^4.5.2 | Client-side global state management |
| `@shopify/flash-list` | 1.6.4 | High-performance virtualised list (replaces FlatList) |
| `victory-native` | ^41.5.0 | Charts (sentiment trend lines) |
| `react-native-chart-kit` | ^6.12.0 | Additional chart components |
| `react-native-reanimated` | ~3.10.0 | Smooth animations |
| `react-native-gesture-handler` | ~2.16.0 | Touch gesture handling |
| `expo-linear-gradient` | ~13.0.2 | Gradient backgrounds/cards |
| `@expo/vector-icons` | ^14.0.0 | Icon set |
| `@react-native-community/netinfo` | 11.3.1 | Network/WebSocket connection state |

**Environment variables:**
- `EXPO_PUBLIC_API_URL` — REST base URL, e.g. `http://localhost:8080`
- `EXPO_PUBLIC_WS_URL` — WebSocket URL, e.g. `ws://localhost:8085/ws/rankings`

---

## Infrastructure

### Kafka Topics

All topics use Confluent Platform 7.6.0. Kafka is exposed on `localhost:9092` for local development; internal services use `kafka:29092`.

| Topic | Partitions | Retention | Compression | Notes |
|---|---|---|---|---|
| `raw-mentions` | 12 | 1 year | LZ4 | Max message 10MB. Raw scraped posts before scoring. |
| `scored-mentions` | 12 | 1 year | LZ4 | Posts with FinBERT sentiment label + score attached. |
| `live-rankings` | 6 | 1 hour | — | Compacted (latest value per ticker key only). Drives WebSocket pushes. |
| `stock-fundamentals` | 4 | default | — | Fundamentals from FMP API. |
| `stock-prices` | 4 | default | — | Price data from Alpha Vantage. |

A **Kafka UI** (provectuslabs/kafka-ui) is available at `http://localhost:8090` for browsing topics, consumer groups, and message payloads.

### Elasticsearch Index

Version: 8.13.0 (security disabled for local dev). Kibana available at `http://localhost:5601`.  
Index settings: **3 shards**, 1 replica, 5-second refresh interval.

Each document stored in Elasticsearch represents one scored mention:

| Field | ES Type | Description |
|---|---|---|
| `ticker` | keyword | Stock symbol, e.g. `TSLA` |
| `source` | keyword | Origin platform (`twitter`, `reddit`, `wsb`, `seekingalpha`, `yahoo`) |
| `raw_text` | text (english analyser) | Original post/comment text, full-text searchable |
| `sentiment_score` | float | FinBERT confidence score 0–1 |
| `sentiment_label` | keyword | `positive` / `negative` / `neutral` |
| `influencer_weight` | float | Author influence multiplier (follower proxy) |
| `composite_score` | float | Final weighted score used for ranking |
| `upvotes` | integer | Post/comment upvote count |
| `author` | keyword | Username or handle |
| `timestamp` | date | UTC publish time of original post |

### Redis

Version: 7.2-alpine. Configuration (`infrastructure/redis/redis.conf`):
- **Max memory:** 2GB, eviction policy `allkeys-lru`
- **Persistence:** RDB snapshots (900s/1 change, 300s/10 changes, 60s/10000 changes) + AOF with `appendfsync everysec`
- **hz:** 100 (high-frequency background tasks)
- **tcp-keepalive:** 300s

Redis is used by the Sentiment Aggregator for hot-path aggregation state caching, and by the API Gateway for response caching.

### PostgreSQL / TimescaleDB

Image: `timescale/timescaledb:latest-pg16` — PostgreSQL 16 with the TimescaleDB extension for efficient time-series data storage and queries. Used to persist historical sentiment scores per ticker over time (hypertables partitioned by timestamp), enabling trend analysis in the mobile app charts.

Database name: `stockpulse`, user: `stockpulse`.  
Initialised via `infrastructure/postgres/init.sql` (mounted at container start).

---

## Port Map

| Port | Service | Notes |
|---|---|---|
| 8080 | API Gateway | REST API for mobile app |
| 8082 | Sentiment Aggregator | Internal only |
| 8083 | Stock Data Service | Internal only |
| 8085 | WebSocket Service | Mobile app WebSocket connection |
| 5000 | FinBERT Service | Called by scrapers; internal |
| 9092 | Kafka | External (dev access) |
| 5432 | PostgreSQL | External (dev access) |
| 6379 | Redis | External (dev access) |
| 9200 | Elasticsearch | External (dev access) |
| 5601 | Kibana | Elasticsearch UI |
| 8090 | Kafka UI | Kafka browser UI |

---

## Environment Variables

Copy `.env.example` to `.env` and fill in the secrets:

```env
# Infrastructure
KAFKA_BROKERS=kafka:29092
POSTGRES_URL=jdbc:postgresql://postgres:5432/stockpulse
POSTGRES_USER=stockpulse
POSTGRES_PASSWORD=stockpulse_secret
REDIS_HOST=redis
REDIS_PORT=6379
ES_URL=http://elasticsearch:9200

# External API keys (required for Stock Data Service)
ALPHA_VANTAGE_KEY=your_key_here       # https://www.alphavantage.co
FMP_API_KEY=your_key_here             # https://financialmodelingprep.com

# Reddit API (required for Reddit/WSB scraper)
REDDIT_CLIENT_ID=your_reddit_app_id
REDDIT_SECRET=your_reddit_secret

# Optional: HTTP proxy rotation for scrapers
PROXY_LIST=                           # Comma-separated proxy URLs

# Mobile app (set in services/mobile-app/.env)
EXPO_PUBLIC_API_URL=http://localhost:8080
EXPO_PUBLIC_WS_URL=ws://localhost:8085/ws/rankings
```

---

## Tech Stack Summary

| Layer | Technology | Version |
|---|---|---|
| **Scraping** | Node.js + Playwright + playwright-extra stealth | Playwright 1.44 |
| **Anti-bot** | fingerprint-generator, user-agents, stealth plugin | — |
| **Message broker** | Apache Kafka (Confluent Platform) + Zookeeper | CP 7.6.0 |
| **AI / Sentiment** | Python, FastAPI, FinBERT (Hugging Face Transformers), PyTorch | Python 3.11, Torch 2.3 |
| **Aggregation** | Java 25, Spring Boot, ZGC GC | Java 25 |
| **Stock data** | Java 25, Spring Boot, Alpha Vantage API, FMP API | Java 25 |
| **API** | Java 25, Spring Boot REST | Java 25 |
| **Real-time push** | Java 25, Spring Boot WebSocket | Java 25 |
| **Search / analytics** | Elasticsearch 8.13 + Kibana | 8.13.0 |
| **Cache** | Redis 7.2 | 7.2-alpine |
| **Time-series DB** | PostgreSQL 16 + TimescaleDB | PG16 |
| **Mobile app** | React Native 0.74, Expo 51, TypeScript 5.3 | Expo SDK 51 |
| **Mobile state** | TanStack Query v5, Zustand | — |
| **Mobile charts** | Victory Native, react-native-chart-kit | — |
| **Mobile navigation** | expo-router (file-system routing) | ^3.5.0 |
| **Infrastructure** | Docker, Docker Compose v3.9 | — |
| **Observability** | Prometheus client (FinBERT), Kafka UI, Kibana | — |

---

## Quick Start

```bash
# 1. Clone and configure environment
cp .env.example .env
# Edit .env — fill in ALPHA_VANTAGE_KEY, FMP_API_KEY, REDDIT_CLIENT_ID, REDDIT_SECRET

# 2. Start infrastructure (Kafka, Zookeeper, Postgres, Redis, Elasticsearch)
make infra

# 3. Create Kafka topics
make topics

# 4. Build and start all application services
make up

# 5. Start the mobile app (separate terminal)
cd services/mobile-app
npm install
expo start
```

**Useful commands:**
```bash
make logs    # Tail logs from all services
make build   # Rebuild all Docker images in parallel
make down    # Stop all containers
make clean   # Stop containers and delete all volumes (destructive)
```

**Dev UIs:**
- Kafka UI: http://localhost:8090
- Kibana: http://localhost:5601
- Elasticsearch: http://localhost:9200
- API Gateway: http://localhost:8080
