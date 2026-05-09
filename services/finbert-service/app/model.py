from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch
import torch.nn.functional as F
import asyncio
import logging
import os

logger = logging.getLogger(__name__)

class FinBERTModel:
    MODEL_NAME = "ProsusAI/finbert"

    def __init__(self):
        self.tokenizer = None
        self.model     = None
        self.device    = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.labels    = ["POSITIVE", "NEGATIVE", "NEUTRAL"]
        self._lock     = asyncio.Lock()

    async def load(self):
        cache_dir = os.getenv("MODEL_CACHE_DIR", "/models")
        logger.info(f"Loading {self.MODEL_NAME} on {self.device}")

        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, self._load_sync, cache_dir)

    def _load_sync(self, cache_dir):
        self.tokenizer = AutoTokenizer.from_pretrained(
            self.MODEL_NAME, cache_dir=cache_dir
        )
        self.model = AutoModelForSequenceClassification.from_pretrained(
            self.MODEL_NAME, cache_dir=cache_dir
        ).to(self.device)
        self.model.eval()

    async def predict(self, text: str) -> dict:
        async with self._lock:
            loop = asyncio.get_event_loop()
            return await loop.run_in_executor(None, self._predict_sync, text)

    def _predict_sync(self, text: str) -> dict:
        # Truncate to 512 tokens
        inputs = self.tokenizer(
            text,
            return_tensors="pt",
            truncation=True,
            max_length=512,
            padding=True
        ).to(self.device)

        with torch.no_grad():
            outputs = self.model(**inputs)
            probs   = F.softmax(outputs.logits, dim=-1)[0]

        scores = {label: float(probs[i]) for i, label in enumerate(self.labels)}
        predicted_label = max(scores, key=scores.get)
        confidence      = scores[predicted_label]

        return {
            "finbert_scores": scores,
            "sentiment_label": predicted_label,
            "confidence": confidence,
        }