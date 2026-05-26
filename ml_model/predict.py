"""
Phase 3: FastAPI inference endpoint for BERT-Mini anomaly detection.

Endpoints:
    POST /predict  - Classify an HTTP request log
    GET  /health   - Health check

Usage:
    uvicorn predict:app --host 0.0.0.0 --port 8000
"""
import sys
from pathlib import Path

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoTokenizer, BertForSequenceClassification

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402


# ── FastAPI app ───────────────────────────────────────────────────────────────
app = FastAPI(
    title="BERT Webhook Anomaly Detection",
    description="Classify HTTP request logs as normal or anomalous using BERT-Mini.",
    version="1.0.0",
)


# ── Global model cache ────────────────────────────────────────────────────────
_model = None
_tokenizer = None
_device = torch.device("cuda" if torch.cuda.is_available() else "cpu")


# Required model files to consider saved_model valid
_REQUIRED_MODEL_FILES = ["config.json", "model.safetensors"]


def _is_model_valid(path: Path) -> bool:
    """Check if the saved model directory contains all required files."""
    if not path.exists() or not path.is_dir():
        return False
    model_files = {f.name for f in path.iterdir() if f.is_file()}
    return all(rf in model_files for rf in _REQUIRED_MODEL_FILES)


def load_model():
    """Load model and tokenizer from saved_model/ (lazy, on first request)."""
    global _model, _tokenizer

    if _model is not None and _tokenizer is not None:
        return _model, _tokenizer

    model_path = config.SAVED_MODEL_DIR

    if not _is_model_valid(model_path):
        raise RuntimeError(
            f"No valid model found at {model_path}. "
            f"Run train.py first to train and save a model."
        )

    print(f"[predict] Loading model from {model_path} ...")
    _tokenizer = AutoTokenizer.from_pretrained(str(model_path))
    _model = BertForSequenceClassification.from_pretrained(
        str(model_path),
        num_labels=config.NUM_LABELS,
    )
    _model.to(_device)
    _model.eval()
    print(f"[predict] Model loaded on {_device}.")

    return _model, _tokenizer


# ── Pydantic schemas ──────────────────────────────────────────────────────────
class PredictRequest(BaseModel):
    text_input: str = Field(default="", description="Raw text_input (nếu có sẽ dùng thẳng, bỏ qua các field khác)")
    source: str = Field(default="http", description="Event source: http | kafka")
    method: str = Field(default="", description="HTTP method or Kafka event_type")
    url: str = Field(default="", description="HTTP URL path or Kafka topic name")
    query_params: str = Field(default="", description="HTTP query string or Kafka message key")
    request_payload: str = Field(default="", description="HTTP POST body or Kafka message payload")
    user_agent: str = Field(default="", description="HTTP User-Agent or Kafka producer_id")
    content_type: str = Field(default="", description="Content-Type header")
    cookie: str = Field(default="", description="HTTP Cookie or Kafka headers (serialized)")
    host: str = Field(default="", description="HTTP Host header or Kafka broker/cluster")
    referer: str = Field(default="", description="Referer header (optional)")
    authorization: str = Field(default="", description="Authorization header (optional)")
    timestamp: str = Field(default="", description="ISO 8601 timestamp (optional)")


class PredictResponse(BaseModel):
    label: int = Field(..., description="Predicted class: 0=normal, 1=anomaly")
    is_anomaly: bool = Field(..., description="True if anomaly score > threshold")
    anomaly_score: float = Field(..., description="Probability of anomaly class")
    normal_score: float = Field(..., description="Probability of normal class")


class HealthResponse(BaseModel):
    status: str = Field(..., description="Service status")
    model: str = Field(..., description="Model name")
    model_path: str = Field(..., description="Path to the saved model")


# ── Helpers ───────────────────────────────────────────────────────────────────
def build_text_input(method: str, url_path: str, query_params: str,
                     payload: str, user_agent: str,
                     content_type: str = "", cookie: str = "",
                     host: str = "", referer: str = "",
                     authorization: str = "",
                     source: str = "http",
                     timestamp: str = "") -> str:
    """Build the BERT text input using [SEP] separator.
    source='http' => prefix [HTTP], source='kafka' => prefix [KAFKA]."""
    prefix = "[KAFKA]" if source.lower() == "kafka" else "[HTTP]"
    parts = [
        prefix,
        method.upper() if source.lower() == "http" else method,
        url_path,
        "[SEP]",
        query_params if query_params else "",
        payload if payload else "",
        "[SEP]",
        user_agent if user_agent else "",
        "[SEP]",
        content_type if content_type else "",
        cookie if cookie else "",
        host if host else "",
        referer if referer else "",
        authorization if authorization else "",
        f"[{timestamp}]" if timestamp else "",
    ]
    return " ".join(parts)


@torch.no_grad()
def predict(text: str) -> dict:
    """Run inference on a single text input."""
    model, tokenizer = load_model()

    encoding = tokenizer(
        text,
        truncation=True,
        padding="max_length",
        max_length=config.MAX_LEN,
        return_tensors="pt",
    )

    input_ids = encoding["input_ids"].to(_device)
    attention_mask = encoding["attention_mask"].to(_device)

    outputs = model(input_ids=input_ids, attention_mask=attention_mask)
    probs = torch.softmax(outputs.logits, dim=-1).cpu().numpy()[0]

    anomaly_score = float(probs[1])
    normal_score = float(probs[0])
    label = 1 if anomaly_score >= config.ANOMALY_THRESHOLD else 0

    return {
        "label": label,
        "is_anomaly": bool(label == 1),
        "anomaly_score": anomaly_score,
        "normal_score": normal_score,
    }


# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.on_event("startup")
async def startup():
    """Pre-load model on startup."""
    try:
        load_model()
    except RuntimeError as e:
        print(f"[predict] WARNING: {e}")
    except Exception as e:
        print(f"[predict] WARNING: Unexpected error during model load: {e}")


@app.get("/health", response_model=HealthResponse)
async def health():
    """Health check endpoint."""
    try:
        load_model()
        model_path = str(config.SAVED_MODEL_DIR)
        status = "ok"
    except RuntimeError as e:
        status = f"error: {e}"
        model_path = str(config.SAVED_MODEL_DIR)
    except Exception as e:
        status = f"error: unexpected error - {e}"
        model_path = str(config.SAVED_MODEL_DIR)

    return HealthResponse(
        status=status,
        model=config.MODEL_NAME,
        model_path=model_path,
    )


@app.post("/predict", response_model=PredictResponse)
async def predict_endpoint(request: PredictRequest):
    """
    Classify an HTTP request log as normal or anomalous.

    Input fields:
    - **method**: HTTP method (e.g. GET, POST)
    - **url**: Request URL path (e.g. /api/login)
    - **query_params**: Query string parameters (optional)
    - **request_payload**: POST body / payload (optional)
    - **user_agent**: User-Agent header (optional)
    """
    # Input: dùng text_input trực tiếp nếu có, nếu không build từ các field
    if request.text_input.strip():
        text = request.text_input.strip()
    else:
        if not request.method.strip():
            raise HTTPException(status_code=400, detail="method is required when text_input is empty")
        if not request.url.strip():
            raise HTTPException(status_code=400, detail="url is required when text_input is empty")
        text = build_text_input(
            method=request.method,
            url_path=request.url,
            query_params=request.query_params,
            payload=request.request_payload,
            user_agent=request.user_agent,
            content_type=request.content_type,
            cookie=request.cookie,
            host=request.host,
            referer=request.referer,
            authorization=request.authorization,
            source=request.source,
            timestamp=request.timestamp,
        )

    try:
        result = predict(text)
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=f"Inference error: {e}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Unexpected inference error: {e}")

    return PredictResponse(**result)
