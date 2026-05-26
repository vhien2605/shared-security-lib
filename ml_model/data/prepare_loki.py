"""
Phase 1b: Prepare Loki-expLLM dataset.

Downloads squad-rnd/loki-expLLM from HuggingFace, applies rule-based
anomaly detection, only keeps anomaly samples, and outputs data/loki_anomaly.csv.

Loki schema (from HuggingFace):
    request_timestamp, response_timestamp, client_ip_hash,
    method, url, query_params, request_payload, user_agent,
    response_status, response_body, flagged_by, kill_chain_phase, source

The 'source' field indicates the experiment (exp0, exp1, exp2).
"""
import re
import sys
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402


# ── Rule-based anomaly detection ──────────────────────────────────────────────
_SUSPICIOUS_PAYLOAD = re.compile(
    r"(?i)"
    r"(?:<script|<iframe|<embed|<object"
    r"|union\s+select|select\s+.*\s+from"
    r"|drop\s+table|exec\s*\("
    r"|system\(|passthru\(|shell_exec"
    r"|eval\(|base64_decode"
    r"|\.\.\/|\.\.\\\\|%2e%2e"
    r"|etc/passwd|boot\.ini"
    r"|cmd\.exe|powershell"
    r"|wget\s+|curl\s+"
    r"|\/etc\/|\/usr\/bin"
    r"|alert\(|prompt\(|confirm\("
    r"|fromcharcode|javascript:)"
)

_BLACKLISTED_UA = re.compile(
    r"(?i)"
    r"(?:sqlmap"
    r"|nikto"
    r"|nmap"
    r"|gobuster"
    r"|dirbuster"
    r"|masscan"
    r"|zap"
    r"|burp"
    r"|acunetix"
    r"|nessus"
    r"|openvas"
    r"|python-requests"
    r"|python3"
    r"|python-urllib"
    r"|curl\s*[/\d]"
    r"|wget\s*[/\d]"
    r"|scrapy"
    r"|java/|okhttp)"
)

# High-rate scanning indicators in query params (many params, extreme values)
_SCAN_QUERY = re.compile(r"(?i)(page=\d{4,}|id=-\d+|limit=10000|offset=\d{4,})")


def is_anomaly(row: dict) -> bool:
    """
    Rule-based anomaly detection for Loki samples.
    Returns True if the sample is likely anomalous.
    Loki is a honeypot dataset so most traffic is malicious.
    """
    # Check response status: many anomalies have error statuses
    status = row.get("response_status", -1)
    if status in (403, 404, 405, 406, 500, 501, 502, 503):
        return True

    # Check flagged_by (Loki's own flagging)
    flagged = str(row.get("flagged_by", "")).strip().lower()
    if flagged and flagged != "none":
        return True

    # Check payload
    payload = str(row.get("request_payload", "") or "")
    if len(payload) > 0 and _SUSPICIOUS_PAYLOAD.search(payload):
        return True

    # Check query params
    qp = str(row.get("query_params", "") or "")
    if _SUSPICIOUS_PAYLOAD.search(qp) or _SCAN_QUERY.search(qp):
        return True

    # Check user-agent
    ua = str(row.get("user_agent", "") or "")
    if _BLACKLISTED_UA.search(ua):
        return True

    # Check URL path
    url = str(row.get("url", "") or "")
    if _SUSPICIOUS_PAYLOAD.search(url):
        return True

    # Check kill_chain_phase
    kcp = str(row.get("kill_chain_phase", "") or "").strip().lower()
    if kcp and kcp not in ("", "none", "unknown"):
        return True

    return False


def build_text_input_from_row(row: dict) -> str:
    """Build the BERT text input using [SEP] separator from Loki row."""
    method = str(row.get("method", "") or "").strip().upper()

    # Parse URL to get path
    url = str(row.get("url", "") or "").strip()
    from urllib.parse import urlparse
    if url.startswith("http"):
        parsed = urlparse(url)
        url_path = parsed.path if parsed.path else "/"
        # Override query_params with parsed query if Loki's field is empty
        qp = parsed.query if parsed.query else str(row.get("query_params", "") or "")
    else:
        url_path = url if url.startswith("/") else "/" + url
        qp = str(row.get("query_params", "") or "")

    payload = str(row.get("request_payload", "") or "")
    ua = str(row.get("user_agent", "") or "")
    ct = str(row.get("content_type", "") or "")
    ck = str(row.get("cookie", "") or "")
    ho = str(row.get("host", "") or "")
    rf = str(row.get("referer", "") or "")
    au = str(row.get("authorization", "") or "")

    parts = ["[HTTP]", method, url_path, "[SEP]", qp, payload, "[SEP]", ua,
             "[SEP]", ct, ck, ho, rf, au]
    return " ".join(parts)


def detect_attack_type_from_row(row: dict) -> str:
    """Detect attack type for Loki sample."""
    payload = str(row.get("request_payload", "") or "")
    qp = str(row.get("query_params", "") or "")
    url = str(row.get("url", "") or "")
    combined = f"{url} {qp} {payload}"

    if re.search(r"(?i)(union\s+select|select\s+.*\s+from|drop\s+table|--|')", combined):
        return "sqli"
    if re.search(r"(?i)(<script|<iframe|javascript:|alert\(|onerror|onload)", combined):
        return "xss"
    if re.search(r"(?i)(\.\.\/|\.\.\\\\|%2e%2e|etc/passwd)", combined):
        return "path_traversal"
    if re.search(r"(?i)(wget\s|curl\s|bash\s|powershell|cmd\.exe|system\()", combined):
        return "rce"

    status = row.get("response_status", -1)
    if status in (403, 404):
        return "scanning"
    if status in (405, 406, 500):
        return "other_anomaly"

    return "other_anomaly"


def main():
    print(f"[prepare_loki] Loading {config.LOKI_HF_DATASET} from HuggingFace ...")
    from datasets import load_dataset

    ds = load_dataset(
        config.LOKI_HF_DATASET,
        split="train",
        streaming=False,
    )

    total_samples = config.LOKI_MAX_SAMPLES or len(ds)
    print(f"[prepare_loki] Total samples in dataset: {len(ds)}")

    rows = []
    anomaly_count = 0
    for i, sample in enumerate(ds):
        if i >= total_samples:
            break

        if is_anomaly(sample):
            anomaly_count += 1
            text_input = build_text_input_from_row(sample)
            attack_type = detect_attack_type_from_row(sample)

            # Determine source
            source = str(sample.get("source", "") or "").strip()
            if source:
                source = f"loki_{source}"
            else:
                source = "loki"

            rows.append({
                "text_input": text_input,
                "method": str(sample.get("method", "") or "").strip().upper(),
                "url_path": text_input.split(" ")[1] if len(text_input.split(" ")) > 1 else "/",
                "query_params": str(sample.get("query_params", "") or ""),
                "request_payload": str(sample.get("request_payload", "") or ""),
                "user_agent": str(sample.get("user_agent", "") or ""),
                "content_type": str(sample.get("content_type", "") or ""),
                "cookie": str(sample.get("cookie", "") or ""),
                "host": str(sample.get("host", "") or ""),
                "referer": str(sample.get("referer", "") or ""),
                "authorization": str(sample.get("authorization", "") or ""),
                "response_status": sample.get("response_status", -1),
                "label": 1,  # Loki samples are all anomalies
                "attack_type": attack_type,
                "source": source,
            })

        if (i + 1) % 1000 == 0:
            print(f"[prepare_loki] Processed {i+1}/{total_samples} ...")

    df = pd.DataFrame(rows)
    print(f"[prepare_loki] Found {anomaly_count} anomaly samples out of {total_samples} processed.")

    # ── Fix url_path ─────────────────────────────────────────────────────────
    # Re-extract url_path properly from URL column
    url_paths = []
    for _, row_data in df.iterrows():
        input_text = row_data["text_input"]
        parts = input_text.split(" ")
        # Format: METHOD path [SEP] ...
        if len(parts) >= 2:
            url_paths.append(parts[1])
        else:
            url_paths.append("/")
    df["url_path"] = url_paths

    # ── Write output ─────────────────────────────────────────────────────────
    df.to_csv(config.LOKI_PROCESSED_PATH, index=False)
    print(f"[prepare_loki] Done. Wrote {len(df)} rows to {config.LOKI_PROCESSED_PATH}")
    if not df.empty:
        print(f"    Attack types: {df['attack_type'].value_counts().to_dict()}")
        print(f"    Sources: {df['source'].value_counts().to_dict()}")


if __name__ == "__main__":
    main()
