import re
import logging

logger = logging.getLogger(__name__)

class SentimentScorer:
    """
    Converts FinBERT output to normalized [-1, 1] sentiment score
    with ticker-aware text preprocessing.
    """

    # Boost sentiment for financial jargon
    BULLISH_AMPLIFIERS = {
        'moon', 'rocket', 'calls', 'yolo', 'tendies', 'bullish',
        'breakout', 'squeeze', 'buy', 'long', 'undervalued', 'hidden gem',
    }
    BEARISH_AMPLIFIERS = {
        'puts', 'short', 'dump', 'crash', 'bubble', 'overvalued',
        'bearish', 'sell', 'rug', 'fraud', 'scam',
    }

    def __init__(self, model):
        self.model = model

    async def score(self, text: str, ticker: str) -> dict:
        # Preprocess: remove noise, normalize
        cleaned = self.preprocess(text, ticker)

        result = await self.model.predict(cleaned)

        # Convert to [-1, 1] scale
        pos = result["finbert_scores"]["POSITIVE"]
        neg = result["finbert_scores"]["NEGATIVE"]
        neu = result["finbert_scores"]["NEUTRAL"]

        # Sentiment score: positive = +1, negative = -1, neutral = 0
        base_score = pos - neg

        # Apply jargon amplification
        jargon_mult = self.compute_jargon_multiplier(text.lower())
        final_score = max(-1.0, min(1.0, base_score * jargon_mult))

        return {
            "sentiment_score": round(final_score, 4),
            "sentiment_label": result["sentiment_label"],
            "confidence": round(result["confidence"], 4),
            "finbert_scores": result["finbert_scores"],
        }

    def preprocess(self, text: str, ticker: str) -> str:
        # Remove URLs
        text = re.sub(r'http\S+|www\.\S+', '', text)
        # Remove excessive punctuation
        text = re.sub(r'[!]{2,}', '!', text)
        text = re.sub(r'[?]{2,}', '?', text)
        # Remove emojis (keep ASCII)
        text = text.encode('ascii', 'ignore').decode()
        # Remove leading/trailing whitespace
        text = text.strip()
        # If ticker is mentioned, highlight it for FinBERT context
        text = f"Stock {ticker}: {text}"
        return text[:512]

    def compute_jargon_multiplier(self, text: str) -> float:
        words   = set(text.split())
        bullish = len(words & self.BULLISH_AMPLIFIERS)
        bearish = len(words & self.BEARISH_AMPLIFIERS)

        net = bullish - bearish
        if net == 0:   return 1.0
        if net > 0:    return min(1.0 + net * 0.05, 1.3)  # max 1.3x boost
        return max(1.0 + net * 0.05, 0.7)                  # max 0.7x reduction