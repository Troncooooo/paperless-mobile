#!/usr/bin/env python3
"""Gemini PR checker (free tier) — T-31 for pi-dev-tutorial.

Sends a git diff to the Gemini API (no SDK, stdlib only) using one or more
FREE-TIER API keys and prints a markdown review comment to stdout.

Exit codes:
  0 = review produced (markdown on stdout)
  2 = no review possible (all keys failed / rate-limited)
  1 = usage error

Keys come from GEMINI_API_KEYS (comma/space separated — rotate on 429/403)
or GEMINI_API_KEY. This is the "add a free external account" mechanism:
a second free Google account just means a second key in the list.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

DEFAULT_MODEL = "gemini-flash-latest"
API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
MAX_TROTTLE_ATTEMPTS = 1  # per key: retry 429 once after backoff

SYSTEM_PROMPT = """You are a senior code reviewer performing a first-pass review of
a git diff. Be concrete and concise.

Output format (markdown, this goes verbatim into a PR comment):
- First line: a verdict emoji + one sentence: ✅ LGTM (minor nits), ⚠️ Changes
  requested (has real issues), or ❓ Cannot review (diff unusable/truncated to
  nothing useful).
- If there are issues: a bullet list, max 8, each as
  `- [severity] file:line — what is wrong and how to fix it`.
  severity ∈ {blocker, major, minor, nit}. Only real issues; no style filler.
- Last line: a one-sentence overall summary.

Rules: never invent file names or lines that are not in the diff; if the diff
is too small to mean anything, say so (❓). Do not add praise, greetings, or
footers. Do not mention Google, Gemini, or being an AI."""


def load_keys() -> list[str]:
    raw = os.environ.get("GEMINI_API_KEYS", "") or os.environ.get("GEMINI_API_KEY", "")
    return [k.strip() for k in raw.replace(",", " ").split() if k.strip()]


def truncate_diff(text: str, max_lines: int) -> tuple[str, int]:
    lines = text.splitlines(keepends=True)
    if len(lines) <= max_lines:
        return text, 0
    head = int(max_lines * 0.6)
    out = lines[:head]
    out.append(f"\n[diff truncated: {len(lines) - max_lines} of {len(lines)} lines omitted]\n")
    return "".join(out), len(lines) - max_lines


def call_gemini(api_key: str, model: str, diff: str, max_output_tokens: int) -> str:
    payload = {
        "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [
            {"role": "user", "parts": [{"text": f"Review this diff:\n\n```diff\n{diff}\n```"}]}
        ],
        "generationConfig": {"temperature": 0.2, "maxOutputTokens": max_output_tokens},
    }
    req = urllib.request.Request(
        API_URL.format(model=model),
        data=json.dumps(payload).encode(),
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": api_key,
        },
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode())
    parts = data["candidates"][0]["content"]["parts"]
    return "".join(p.get("text", "") for p in parts).strip()


def review(diff: str, keys: list[str], model: str, max_output_tokens: int) -> str:
    """Try keys in order; rotate to the next key on 429/403 (free-tier caps)."""
    last_err = "no keys"
    for i, key in enumerate(keys):
        try:
            return call_gemini(key, model, diff, max_output_tokens)
        except urllib.error.HTTPError as e:
            code = e.code
            body = ""
            try:
                body = e.read().decode()[:300]
            except Exception:
                pass
            if code in (429, 403):
                last_err = f"key[{i}] {code} {body!r}"
                continue  # rotate to next key
            if code == 400 and "API key" in body:
                last_err = f"key[{i}] 400 bad key"
                continue
            raise
        except urllib.error.URLError as e:
            last_err = f"key[{i}] network: {e.reason}"
            continue
    raise SystemExit(
        f"gemini_review: no key produced a review (last error: {last_err}). "
        "If 429: free-tier cap hit — wait, lower traffic, or add another free key "
        "to GEMINI_API_KEYS."
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--diff", help="path to diff file (default: stdin)")
    ap.add_argument("--model", default=os.environ.get("GEMINI_MODEL", DEFAULT_MODEL))
    ap.add_argument("--max-diff-lines", type=int, default=int(os.environ.get("GEMINI_MAX_DIFF_LINES", 4000)))
    ap.add_argument("--max-output-tokens", type=int, default=1024)
    args = ap.parse_args()

    diff = open(args.diff).read() if args.diff else sys.stdin.read()
    diff, omitted = truncate_diff(diff, args.max_diff_lines)
    if not diff.strip():
        print("❓ Cannot review — empty diff.")
        return 0

    keys = load_keys()
    if not keys:
        print("gemini_review: no GEMINI_API_KEY/GEMINI_API_KEYS set", file=sys.stderr)
        return 1

    result = review(diff, keys, args.model, args.max_output_tokens)
    print(result)
    if omitted:
        print(f"\n_(reviewed first {args.max_diff_lines} lines, {omitted} omitted)_")
    return 0


if __name__ == "__main__":
    sys.exit(main())
