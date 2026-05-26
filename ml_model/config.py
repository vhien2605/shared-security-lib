"""
Central configuration for the BERT Webhook Anomaly Detection system.
All paths are relative to the ml_model/ directory.
"""
import os
from pathlib import Path

# ── Base directories ──────────────────────────────────────────────────────────
BASE_DIR = Path(__file__).resolve().parent  # ml_model/
DATA_DIR = BASE_DIR / "data"
PROCESSED_DIR = DATA_DIR / "processed"
SAVED_MODEL_DIR = BASE_DIR / "saved_model"

# Ensure directories exist
for d in [DATA_DIR, PROCESSED_DIR, SAVED_MODEL_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# ── Paths to raw data ─────────────────────────────────────────────────────────
CSIC_RAW_PATH = BASE_DIR.parent / "csic_database.csv"          # mini-project/csic_database.csv
CSIC_PROCESSED_PATH = DATA_DIR / "csic_processed.csv"
LOKI_PROCESSED_PATH = DATA_DIR / "loki_anomaly.csv"

# ── Paths to splits ───────────────────────────────────────────────────────────
TRAIN_PATH = PROCESSED_DIR / "train.csv"
VAL_PATH = PROCESSED_DIR / "val.csv"
TEST_PATH = PROCESSED_DIR / "test.csv"

# ── Model ─────────────────────────────────────────────────────────────────────
MODEL_NAME = "google/bert_uncased_L-4_H-256_A-4"  # BERT-Mini, 11M params
MAX_LEN = 256  # Tăng do có prefix [HTTP]/[KAFKA] + event_type cho Kafka
NUM_LABELS = 2  # 0 = normal, 1 = anomaly

# ── Training hyperparameters ──────────────────────────────────────────────────
BATCH_SIZE = 32
EPOCHS = 5
LR = 2e-5
WARMUP_RATIO = 0.1
EARLY_STOP_PATIENCE = 2
RANDOM_SEED = 42

# ── Split ratios ──────────────────────────────────────────────────────────────
TRAIN_RATIO = 0.70
VAL_RATIO = 0.15
TEST_RATIO = 0.15

# ── Anomaly threshold for inference ───────────────────────────────────────────
ANOMALY_THRESHOLD = 0.5

# ── Loki dataset ──────────────────────────────────────────────────────────────
LOKI_HF_DATASET = "squad-rnd/loki-expLLM"
LOKI_MAX_SAMPLES = None  # Set to an int to limit for testing

# ── Device ────────────────────────────────────────────────────────────────────
DEVICE = "cpu"  # Will be auto-detected in train.py/predict.py
