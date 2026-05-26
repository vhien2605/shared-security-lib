"""
Phase 2: Fine-tune BERT-Mini on the anomaly detection dataset.

Reads data/processed/{train,val}.csv, fine-tunes BERT-Mini with early
stopping on validation F1, and saves the best model to saved_model/.
Finally evaluates on data/processed/test.csv.
"""
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    classification_report, confusion_matrix,
)
from torch.utils.data import Dataset, DataLoader
from transformers import (
    AutoTokenizer,
    BertForSequenceClassification,
    get_linear_schedule_with_warmup,
)

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402


# ── Set random seeds for reproducibility ─────────────────────────────────────
def set_seed(seed: int):
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


set_seed(config.RANDOM_SEED)

# ── Device ────────────────────────────────────────────────────────────────────
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"[train] Using device: {device}")


# ── PyTorch Dataset ───────────────────────────────────────────────────────────
class HTTPRequestDataset(Dataset):
    """Dataset that tokenizes text_input on-the-fly."""

    def __init__(self, texts, labels, tokenizer, max_len: int):
        self.texts = texts
        self.labels = labels
        self.tokenizer = tokenizer
        self.max_len = max_len

    def __len__(self):
        return len(self.texts)

    def __getitem__(self, idx):
        text = str(self.texts[idx])
        label = int(self.labels[idx])

        encoding = self.tokenizer(
            text,
            truncation=True,
            padding="max_length",
            max_length=self.max_len,
            return_tensors="pt",
        )

        return {
            "input_ids": encoding["input_ids"].flatten(),
            "attention_mask": encoding["attention_mask"].flatten(),
            "labels": torch.tensor(label, dtype=torch.long),
        }


# ── Load data ─────────────────────────────────────────────────────────────────
def load_split(path: Path):
    df = pd.read_csv(path)
    texts = df["text_input"].fillna("").astype(str).tolist()
    labels = df["label"].fillna(0).astype(int).tolist()
    return texts, labels


print("[train] Loading data ...")
train_texts, train_labels = load_split(config.TRAIN_PATH)
val_texts, val_labels = load_split(config.VAL_PATH)
test_texts, test_labels = load_split(config.TEST_PATH)

print(f"    Train: {len(train_texts)} samples")
print(f"    Val:   {len(val_texts)} samples")
print(f"    Test:  {len(test_texts)} samples")


# ── Tokenizer ─────────────────────────────────────────────────────────────────
print(f"[train] Loading tokenizer: {config.MODEL_NAME}")
tokenizer = AutoTokenizer.from_pretrained(config.MODEL_NAME)

# Create datasets
train_dataset = HTTPRequestDataset(train_texts, train_labels, tokenizer, config.MAX_LEN)
val_dataset = HTTPRequestDataset(val_texts, val_labels, tokenizer, config.MAX_LEN)
test_dataset = HTTPRequestDataset(test_texts, test_labels, tokenizer, config.MAX_LEN)

# DataLoaders
train_loader = DataLoader(
    train_dataset,
    batch_size=config.BATCH_SIZE,
    shuffle=True,
    num_workers=0,  # Windows compat
)
val_loader = DataLoader(
    val_dataset,
    batch_size=config.BATCH_SIZE * 2,
    shuffle=False,
    num_workers=0,
)
test_loader = DataLoader(
    test_dataset,
    batch_size=config.BATCH_SIZE * 2,
    shuffle=False,
    num_workers=0,
)


# ── Model ─────────────────────────────────────────────────────────────────────
print(f"[train] Loading model: {config.MODEL_NAME}")
model = BertForSequenceClassification.from_pretrained(
    config.MODEL_NAME,
    num_labels=config.NUM_LABELS,
)
model.to(device)

# ── Optimizer & Scheduler ─────────────────────────────────────────────────────
optimizer = torch.optim.AdamW(model.parameters(), lr=config.LR)

total_steps = len(train_loader) * config.EPOCHS
warmup_steps = int(total_steps * config.WARMUP_RATIO)

scheduler = get_linear_schedule_with_warmup(
    optimizer,
    num_warmup_steps=warmup_steps,
    num_training_steps=total_steps,
)


# ── Training helpers ──────────────────────────────────────────────────────────
def evaluate(dataloader, model, device) -> dict:
    """Evaluate model on a dataloader, returning metrics."""
    model.eval()
    all_preds = []
    all_labels = []
    total_loss = 0.0

    with torch.no_grad():
        for batch in dataloader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["labels"].to(device)

            outputs = model(
                input_ids=input_ids,
                attention_mask=attention_mask,
                labels=labels,
            )
            total_loss += outputs.loss.item()

            preds = torch.argmax(outputs.logits, dim=-1)
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())

    avg_loss = total_loss / len(dataloader)
    acc = accuracy_score(all_labels, all_preds)
    prec = precision_score(all_labels, all_preds, zero_division=0)
    rec = recall_score(all_labels, all_preds, zero_division=0)
    f1 = f1_score(all_labels, all_preds, zero_division=0)

    return {
        "loss": avg_loss,
        "accuracy": acc,
        "precision": prec,
        "recall": rec,
        "f1": f1,
    }


# ── Training loop ─────────────────────────────────────────────────────────────
print("[train] Starting training ...")
best_val_f1 = 0.0
epochs_no_improve = 0
best_model_state = None

for epoch in range(1, config.EPOCHS + 1):
    model.train()
    total_train_loss = 0.0

    for step, batch in enumerate(train_loader):
        input_ids = batch["input_ids"].to(device)
        attention_mask = batch["attention_mask"].to(device)
        labels = batch["labels"].to(device)

        optimizer.zero_grad()

        outputs = model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            labels=labels,
        )
        loss = outputs.loss
        total_train_loss += loss.item()

        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        scheduler.step()

        if (step + 1) % 50 == 0:
            print(f"    Epoch {epoch}, Step {step+1}/{len(train_loader)}, "
                  f"Loss: {loss.item():.4f}")

    avg_train_loss = total_train_loss / len(train_loader)

    # Evaluate on validation set
    val_metrics = evaluate(val_loader, model, device)
    val_f1 = val_metrics["f1"]

    print(f"\n[Epoch {epoch}/{config.EPOCHS}]")
    print(f"    Train Loss: {avg_train_loss:.4f}")
    print(f"    Val  Loss: {val_metrics['loss']:.4f} | "
          f"Acc: {val_metrics['accuracy']:.4f} | "
          f"Prec: {val_metrics['precision']:.4f} | "
          f"Rec: {val_metrics['recall']:.4f} | "
          f"F1: {val_metrics['f1']:.4f}")

    # Early stopping check (based on F1)
    if val_f1 > best_val_f1:
        best_val_f1 = val_f1
        epochs_no_improve = 0
        best_model_state = model.state_dict()
        print(f"    *** New best model (val F1 = {val_f1:.4f}) ***")
    else:
        epochs_no_improve += 1
        print(f"    No improvement for {epochs_no_improve} epoch(s).")

    if epochs_no_improve >= config.EARLY_STOP_PATIENCE:
        print(f"[train] Early stopping triggered after {epoch} epochs.")
        break

# ── Save best model ───────────────────────────────────────────────────────────
if best_model_state is not None:
    model.load_state_dict(best_model_state)
    model.save_pretrained(config.SAVED_MODEL_DIR)
    tokenizer.save_pretrained(config.SAVED_MODEL_DIR)
    print(f"[train] Best model saved to {config.SAVED_MODEL_DIR}")
else:
    model.save_pretrained(config.SAVED_MODEL_DIR)
    tokenizer.save_pretrained(config.SAVED_MODEL_DIR)
    print(f"[train] Model saved to {config.SAVED_MODEL_DIR} (no improvement)")

# ── Final evaluation on test set ─────────────────────────────────────────────
print("\n[train] Evaluating on test set ...")
test_metrics = evaluate(test_loader, model, device)
print(f"    Test Loss: {test_metrics['loss']:.4f}")
print(f"    Test Accuracy: {test_metrics['accuracy']:.4f}")
print(f"    Test Precision: {test_metrics['precision']:.4f}")
print(f"    Test Recall: {test_metrics['recall']:.4f}")
print(f"    Test F1: {test_metrics['f1']:.4f}")

# Full classification report on test set
model.eval()
all_preds = []
all_labels = []
with torch.no_grad():
    for batch in test_loader:
        input_ids = batch["input_ids"].to(device)
        attention_mask = batch["attention_mask"].to(device)
        labels = batch["labels"].to(device)
        outputs = model(
            input_ids=input_ids,
            attention_mask=attention_mask,
        )
        preds = torch.argmax(outputs.logits, dim=-1)
        all_preds.extend(preds.cpu().numpy())
        all_labels.extend(labels.cpu().numpy())

print("\n[train] Classification Report:")
print(classification_report(all_labels, all_preds,
                            target_names=["normal", "anomaly"]))
print(f"\n[train] Confusion Matrix:")
print(confusion_matrix(all_labels, all_preds))

target_f1 = 0.95
if test_metrics["f1"] >= target_f1:
    print(f"\n[train] SUCCESS: Test F1 = {test_metrics['f1']:.4f} >= {target_f1}")
else:
    print(f"\n[train] WARNING: Test F1 = {test_metrics['f1']:.4f} < {target_f1}")

print("[train] Done.")
