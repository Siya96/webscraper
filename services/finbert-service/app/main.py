from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import asyncio
import logging
from app.model import FinBERTModel
from app.scoring import SentimentScorer
from prometheus_client import Counter, Histogram, make_asgi_app
import time

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="FinBERT Sentiment Service", version="1.0.0")

# Prometheus metrics
REQUESTS  = Counter('finbert_requests_total', 'Total requests')
LATENCY   = Histogram('finbert_latency_seconds', 'Request latency')
ERRORS    = Counter('finbert_errors_total', 'Total errors')

# Mount Prometheus
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)

# Global model instance
finbert_model: Optional[FinBERTModel] = None
scorer: Optional[SentimentScorer]     = None

@app.on_event("startup")
async def startup():
    global finbert_model, scorer
    logger.info("Loading FinBERT model...")
    finbert_model = FinBERTModel()
    await finbert_model.load()
    scorer = SentimentScorer(finbert_model)
    logger.info("FinBERT ready!")

class MentionRequest(BaseModel):
    id: str
    ticker: str
    text: str
    source: str
    author: str
    author_followers: int = 0
    upvotes: int = 0
    timestamp: str

class SentimentResponse(BaseModel):
    id: str
    ticker: str
    sentiment_score: float    # -1.0 to 1.0
    sentiment_label: str      # POSITIVE, NEGATIVE, NEUTRAL
    confidence: float
    finbert_scores: dict      # Raw FinBERT output

class BatchRequest(BaseModel):
    mentions: List[MentionRequest]

class BatchResponse(BaseModel):
    results: List[SentimentResponse]
    processed: int
    failed: int

@app.post("/score", response_model=SentimentResponse)
async def score_single(request: MentionRequest):
    REQUESTS.inc()
    start = time.time()
    try:
        result = await scorer.score(request.text, request.ticker)
        LATENCY.observe(time.time() - start)
        return SentimentResponse(
            id=request.id,
            ticker=request.ticker,
            **result
        )
    except Exception as e:
        ERRORS.inc()
        logger.error(f"Scoring error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/score/batch", response_model=BatchResponse)
async def score_batch(request: BatchRequest):
    REQUESTS.inc()
    results = []
    failed  = 0

    # Process in parallel batches of 16
    semaphore = asyncio.Semaphore(8)
    async def score_one(mention):
        async with semaphore:
            try:
                result = await scorer.score(mention.text, mention.ticker)
                return SentimentResponse(id=mention.id, ticker=mention.ticker, **result)
            except Exception as e:
                nonlocal failed
                failed += 1
                logger.warning(f"Failed to score {mention.id}: {e}")
                return None

    tasks   = [score_one(m) for m in request.mentions]
    outputs = await asyncio.gather(*tasks)
    results = [r for r in outputs if r is not None]

    return BatchResponse(results=results, processed=len(results), failed=failed)

@app.get("/health")
async def health():
    return {"status": "ok", "model": "ProsusAI/finbert"}