"""
Phase 1c: Merge CSIC and Loki datasets, then stratified split.

Concatenates csic_processed.csv and loki_anomaly.csv,
shuffles with fixed seed, and performs stratified 70/15/15 split.
Outputs data/processed/{train,val,test}.csv.
"""
import sys
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402


def main():
    # ── Load processed datasets ──────────────────────────────────────────────
    print(f"[merge_datasets] Loading CSIC: {config.CSIC_PROCESSED_PATH} ...")
    csic_df = pd.read_csv(config.CSIC_PROCESSED_PATH)
    print(f"    CSIC rows: {len(csic_df)}")

    print(f"[merge_datasets] Loading Loki: {config.LOKI_PROCESSED_PATH} ...")
    try:
        loki_df = pd.read_csv(config.LOKI_PROCESSED_PATH)
    except FileNotFoundError:
        print(f"[merge_datasets] WARNING: Loki file not found. Proceeding with CSIC only.")
        loki_df = pd.DataFrame(columns=csic_df.columns)
    print(f"    Loki rows: {len(loki_df)}")

    # ── Ensure same columns ──────────────────────────────────────────────────
    expected_cols = [
        "text_input", "method", "url_path", "query_params",
        "request_payload", "user_agent", "response_status",
        "label", "attack_type", "source",
    ]
    for col in expected_cols:
        if col not in csic_df.columns:
            csic_df[col] = ""
        if col not in loki_df.columns:
            loki_df[col] = ""

    # ── Concatenate ──────────────────────────────────────────────────────────
    combined = pd.concat([csic_df[expected_cols], loki_df[expected_cols]],
                         ignore_index=True)

    # Drop any rows with missing text_input
    before = len(combined)
    combined = combined.dropna(subset=["text_input"])
    combined = combined[combined["text_input"].str.strip() != ""]
    after = len(combined)
    if before != after:
        print(f"[merge_datasets] Dropped {before - after} rows with empty text_input.")

    # ── Shuffle ──────────────────────────────────────────────────────────────
    combined = combined.sample(frac=1, random_state=config.RANDOM_SEED).reset_index(drop=True)

    print(f"[merge_datasets] Combined dataset: {len(combined)} rows")
    print(f"    Normal: {(combined['label'] == 0).sum()}, "
          f"Anomaly: {(combined['label'] == 1).sum()}")

    # ── Stratified split ─────────────────────────────────────────────────────
    X = combined
    y = combined["label"]

    # First split: train vs temp (val + test)
    train_ratio = config.TRAIN_RATIO
    val_ratio = config.VAL_RATIO
    test_ratio = config.TEST_RATIO

    # temp_size = val_ratio + test_ratio
    temp_ratio = val_ratio + test_ratio
    # val_size relative to temp: val_ratio / temp_ratio
    val_size_rel = val_ratio / temp_ratio if temp_ratio > 0 else 0.5

    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y,
        test_size=temp_ratio,
        random_state=config.RANDOM_SEED,
        stratify=y,
    )

    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp,
        test_size=(1 - val_size_rel),
        random_state=config.RANDOM_SEED,
        stratify=y_temp,
    )

    print(f"\n[merge_datasets] Split sizes:")
    print(f"    Train: {len(X_train)} (normal: {(y_train == 0).sum()}, anomaly: {(y_train == 1).sum()})")
    print(f"    Val:   {len(X_val)} (normal: {(y_val == 0).sum()}, anomaly: {(y_val == 1).sum()})")
    print(f"    Test:  {len(X_test)} (normal: {(y_test == 0).sum()}, anomaly: {(y_test == 1).sum()})")

    # ── Save splits ──────────────────────────────────────────────────────────
    X_train.to_csv(config.TRAIN_PATH, index=False)
    X_val.to_csv(config.VAL_PATH, index=False)
    X_test.to_csv(config.TEST_PATH, index=False)

    print(f"[merge_datasets] Files saved:")
    print(f"    Train: {config.TRAIN_PATH}")
    print(f"    Val:   {config.VAL_PATH}")
    print(f"    Test:  {config.TEST_PATH}")
    print("[merge_datasets] Done.")


if __name__ == "__main__":
    main()
