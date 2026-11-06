#!/usr/bin/env python3
"""Probe documented anonymous access using synthetic text and Mobie's real identity.

No credentials, user content, response bodies, alternate endpoints, or retries.
Exit 0 only when both selected free models accept a streaming request.
"""
import json
import urllib.error
import urllib.request

ENDPOINT = "https://opencode.ai/inference/openai/v1/chat/completions"


def check(model):
    payload = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": "Reply OK."}],
        "stream": True,
        "max_tokens": 8,
    }).encode()
    request = urllib.request.Request(ENDPOINT, payload, {
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
        "User-Agent": "Mobie/access-check",
    })
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            accepted = response.status == 200 and "text/event-stream" in response.headers.get("Content-Type", "")
            print(json.dumps({"model": model, "status": response.status, "stream_accepted": accepted}))
            return accepted
    except urllib.error.HTTPError as error:
        body = error.read(16_384).decode("utf-8", errors="replace").lower()
        if "freetiererror" in body or "can only be used from within opencode" in body:
            reason = "free_tier_client_restriction"
        elif "cloudflare" in body or "error code: 1010" in body:
            reason = "edge_access_denied"
        else:
            reason = "access_denied" if error.code in (401, 403) else "http_error"
        print(json.dumps({"model": model, "status": error.code, "reason": reason}))
    except (urllib.error.URLError, TimeoutError):
        print(json.dumps({"model": model, "reason": "connection_failed"}))
    return False


if __name__ == "__main__":
    results = [check(model) for model in ("glm-5.3-flash-free", "deepseek-v4.1-flash-free")]
    raise SystemExit(0 if all(results) else 1)
