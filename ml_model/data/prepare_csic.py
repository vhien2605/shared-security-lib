"""
Phase 1a: Prepare CSIC 2010 dataset.

Reads csic_database.csv, normalizes to the unified schema, detects attack type,
and outputs data/csic_processed.csv.

CSIC CSV layout (17 columns after pandas read):
    Unnamed:0  ->  "Normal" / "Anomalous"  (ground truth)
    Method, User-Agent, Pragma, Cache-Control, Accept, Accept-encoding,
    Accept-charset, language, host, cookie, content-type, connection,
    lenght, content, classification (numeric 0/1, redundant), URL
"""
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

import pandas as pd

# Add parent directory so config can be imported
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402


# ── Attack type detection ─────────────────────────────────────────────────────
# Build patterns as lists to avoid long-line issues
_SQLI_PATTERNS = re.compile(
    r"(?i)"
    r"(?:\bunion\b.*\bselect\b"
    r"|\bselect\b.*\bfrom\b"
    r"|\bdrop\b.*\btable\b"
    r"|\bexec\b.*\(|--|#|/\*"
    r"|';"
    r"|1\s*=\s*1"
    r"|admin'"
    r"|or\s+1=1|or\s+'1'='1"
    r"|char\(|concat\(|substring\("
    r"|waitfor\s+delay|benchmark\()"
)

_XSS_PATTERNS = re.compile(
    r"(?i)"
    r"(?:<script"
    r"|<img.*\bonerror"
    r"|<svg.*\bonload"
    r"|javascript:"
    r"|onclick=|onmouseover="
    r"|alert\(|prompt\(|confirm\("
    r"|document\.cookie|fromcharcode"
    r"|eval\(|expression\("
    r"|<iframe|<embed|<object)"
)

_PATH_TRAVERSAL_PATTERNS = re.compile(
    r"(?i)"
    r"(?:\.\.\/"
    r"|\.\.\\\\"
    r"|\.\.%2f|\.\.%5c|%2e%2e"
    r"|etc/passwd|etc/shadow"
    r"|boot\.ini|windows\\win\.ini)"
)

_RCE_PATTERNS = re.compile(
    r"(?i)"
    r"(?:\bping\b"
    r"|\bwget\b|\bcurl\b"
    r"|\bnc\b.*\-e"
    r"|\bbash\b.*\-i"
    r"|\bexec\b"
    r"|system\(|passthru\(|shell_exec"
    r"|`[^`]+`"
    r"|\$\{"
    r"|\/etc\/|\/usr\/bin"
    r"|cmd\.exe|powershell)"
)


def detect_attack_type(text: str, label: int) -> str:
    """Classify the attack type based on content patterns."""
    if label == 0:
        return "normal"

    text_lower = text.lower()

    if _SQLI_PATTERNS.search(text):
        return "sqli"
    if _XSS_PATTERNS.search(text):
        return "xss"
    if _RCE_PATTERNS.search(text):
        return "rce"
    if _PATH_TRAVERSAL_PATTERNS.search(text):
        return "path_traversal"
    if "scan" in text_lower or len(text) > 2000:
        return "scanning"

    return "other_anomaly"


def parse_url(url_str: str) -> tuple:
    """
    Parse a raw URL string into (url_path, query_params).

    The URL column contains entries like:
      'http://localhost:8080/tienda1/index.jsp HTTP/1.1'
    We strip the trailing protocol version and the host part.
    """
    url_str = url_str.strip()

    # Strip trailing HTTP version like " HTTP/1.1"
    for proto in [" HTTP/1.1", " HTTP/1.0", " HTTP/2"]:
        if url_str.upper().endswith(proto.upper()):
            url_str = url_str[: -len(proto)]
            break

    # If there's no scheme, prepend one so urlparse works
    if not url_str.startswith("http"):
        url_str = "http://localhost" + url_str

    parsed = urlparse(url_str)
    path = parsed.path if parsed.path else "/"
    qp = parsed.query if parsed.query else ""

    return path, qp


def build_text_input(method: str, url_path: str, query_params: str,
                     payload: str, user_agent: str,
                     content_type: str = "", cookie: str = "",
                     host: str = "", referer: str = "",
                     authorization: str = "") -> str:
    """Build the BERT text input using [SEP] separator with [HTTP] prefix."""
    parts = [
        "[HTTP]",
        method,
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
    ]
    return " ".join(parts)


def main():
    print(f"[prepare_csic] Reading {config.CSIC_RAW_PATH} ...")
    df = pd.read_csv(config.CSIC_RAW_PATH)

    # Rename the unnamed first column
    df.rename(columns={df.columns[0]: "_label_str"}, inplace=True)

    # ── Validate and extract labels ──────────────────────────────────────────
    # Column _label_str has "Normal" / "Anomalous"
    label_map = {"Normal": 0, "Anomalous": 1}
    unknown_labels = set(df["_label_str"].unique()) - set(label_map.keys())
    if unknown_labels:
        print(f"[prepare_csic] WARNING: Unknown label values: {unknown_labels}")

    df["label"] = df["_label_str"].map(label_map)

    # ── Parse method ─────────────────────────────────────────────────────────
    df["method"] = df["Method"].fillna("").astype(str).str.strip().str.upper()
    # Some rows have empty method - infer from URL column
    url_method_pattern = re.compile(r"^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH)\s", re.I)
    no_method_mask = df["method"] == ""
    if no_method_mask.any():
        df.loc[no_method_mask, "method"] = (
            df.loc[no_method_mask, "URL"]
            .astype(str)
            .str.extract(url_method_pattern, expand=False)
            .fillna("GET")
            .str.upper()
        )

    # Fill any remaining empty methods
    df["method"] = df["method"].replace("", "GET")

    # ── Parse URL ────────────────────────────────────────────────────────────
    print("[prepare_csic] Parsing URLs ...")
    parsed = df["URL"].astype(str).apply(parse_url)
    df["url_path"] = parsed.apply(lambda x: x[0])
    df["query_params"] = parsed.apply(lambda x: x[1])

    # ── Extract other fields ─────────────────────────────────────────────────
    df["user_agent"] = df["User-Agent"].fillna("").astype(str)
    df["request_payload"] = df["content"].fillna("").astype(str)

    # ── Extract HTTP headers ─────────────────────────────────────────────────
    df["content_type"] = df["content-type"].fillna("").astype(str)
    df["cookie"] = df["cookie"].fillna("").astype(str)
    df["host"] = df["host"].fillna("").astype(str)
    df["referer"] = ""  # CSIC không có Referer
    df["authorization"] = ""  # CSIC không có Authorization

    # ── Response status ──────────────────────────────────────────────────────
    # No explicit response status in CSIC - set -1
    df["response_status"] = -1

    # ── Attack type ──────────────────────────────────────────────────────────
    print("[prepare_csic] Detecting attack types ...")
    combined_text = (
        df["url_path"].fillna("")
        + " "
        + df["query_params"].fillna("")
        + " "
        + df["request_payload"].fillna("")
        + " "
        + df["user_agent"].fillna("")
    )

    # Default all to "normal" first
    df["attack_type"] = "normal"

    # Re-detect for anomaly rows
    anomaly_mask = df["label"] == 1
    df.loc[anomaly_mask, "attack_type"] = combined_text.loc[anomaly_mask].apply(
        lambda t: detect_attack_type(t, 1)
    )

    # ── Source ───────────────────────────────────────────────────────────────
    df["source"] = "csic2010"

    # ── Build text_input ─────────────────────────────────────────────────────
    print("[prepare_csic] Building text_input ...")
    df["text_input"] = df.apply(
        lambda r: build_text_input(
            method=r["method"],
            url_path=r["url_path"],
            query_params=r["query_params"],
            payload=r["request_payload"],
            user_agent=r["user_agent"],
            content_type=r["content_type"],
            cookie=r["cookie"],
            host=r["host"],
            referer=r["referer"],
            authorization=r["authorization"],
        ),
        axis=1,
    )

    # ── Select final columns ─────────────────────────────────────────────────
    output_cols = [
        "text_input", "method", "url_path", "query_params",
        "request_payload", "user_agent",
        "content_type", "cookie", "host", "referer", "authorization",
        "response_status",
        "label", "attack_type", "source",
    ]
    out_df = df[output_cols].copy()

    # ── Write output ─────────────────────────────────────────────────────────
    out_df.to_csv(config.CSIC_PROCESSED_PATH, index=False)
    print(f"[prepare_csic] Done. Wrote {len(out_df)} rows to {config.CSIC_PROCESSED_PATH}")
    print(f"    Normal: {(out_df['label'] == 0).sum()}, "
          f"Anomaly: {(out_df['label'] == 1).sum()}")
    print(f"    Attack types: {out_df['attack_type'].value_counts().to_dict()}")


if __name__ == "__main__":
    main()
