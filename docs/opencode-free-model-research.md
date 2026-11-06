# OpenCode free models in Mobie

Checked 2026-10-18. This is a feasibility investigation; no Android implementation or runtime validation was performed.

## Finding

An optional cloud model provider is technically feasible without bundling an OpenCode process. The current official [Inference API documentation](https://opencode.ai/v2/docs/console/api/inference/) explicitly allows free chat models without an authorization header. Mobie could send chat requests directly to `POST https://opencode.ai/inference/openai/v1/chat/completions` and discover model metadata with `GET https://opencode.ai/inference/v1/models`.

This is public API documentation for direct callers, rather than an inference from anonymous access in the OpenCode CLI. The [current model documentation](https://opencode.ai/v2/docs/console/models/#endpoints) also identifies SDK packages as being for applications calling endpoints directly. It does not establish an availability guarantee or an unrestricted free-service entitlement for a distributed consumer application.

## Important limits

- Free offers are temporary. The [free-model documentation](https://opencode.ai/v2/docs/console/models/#free-models) repeatedly describes them as available for a limited period. Do not promise that specific models, or a free tier itself, will remain available indefinitely.
- The reviewed free-model and API documentation does not publish a universal free reset interval or maximum wait. The user's assumption that waiting a few hours always restores access is unverified. Preserve service errors and retry timing when provided; do not invent a countdown.
- Privacy varies by model. [Official privacy details](https://opencode.ai/v2/docs/console/models/#privacy) allow improvement use for several free models; some retain data. NVIDIA trial endpoints exclude personal/confidential submissions. Other free models state zero retention and no training. Show model-specific disclosure before sending data.
- The historical Zen docs differ from current Console/inference docs. Use current endpoints and current model metadata. The official [legacy inference proxy source](https://github.com/anomalyco/opencode/blob/214fb7fdbab9ad13773629839f1178382400cf97/packages/console/app/src/lib/inference-proxy.ts) forwards old Zen routes to new inference, strips absent/`public` credentials for anonymous requests, and delegates authentication/free access to the new service.

## Mobie design implications

These are proposed engineering choices, not claims about an existing feature:

1. Add an explicitly selected Cloud/OpenCode option beside local models. Keep local inference the default and require deliberate selection before transmitting a conversation.
2. Add a remote chat provider boundary. Do not pretend cloud models are downloadable LiteRT artifacts or assign them a local `ModelFormat`; preserve `RuntimeAdapter` as the local engine contract.
3. Fetch and filter current free chat offerings, keeping known protocol and capability support explicit. Handle removed models, network failures, denied access, and rate limits as normal states.
4. Send requests directly from the device unless a backend is independently needed. No bundled CLI/server instance is necessary for model inference. An OpenCode server would be a different integration for its agent/session/tool orchestration.
5. Update the repository's local-only product requirement if implementation is authorized: cloud prompts and attached context necessarily leave the device. This research does not change that requirement or send app data anywhere.

## Probe and confidence

One harmless request was attempted with no authorization, model `glm-5.3-flash-free`, synthetic input `Reply OK.`, and `max_tokens: 8`. It returned HTTP 403 with body `error code: 1010`; the cause was not established. This proves neither successful inference nor rejection of all anonymous clients. Do not claim this integration works on Android until it has been implemented and tested there.

The positive feasibility conclusion rests on current official API documentation, not a successful inference probe. No user prompts, tokens, or model artifacts were submitted during this investigation.

## Phone test follow-up, 2026-10-18

The user reported denials from both GLM 5.3 Flash Free and DeepSeek V4.1 Flash Free. The first APK mapped HTTP 401 and 403 to one generic message, so that report alone does not identify which access gate rejected the phone request. The reproducible probe `python scripts/check-opencode-free-access.py` uses synthetic text, Mobie's own identity, no credentials and no retries. Both models currently return HTTP 403 with an explicit free-tier client restriction from this development environment. A separate request using Python's default client identity received an HTML security-service denial, demonstrating why the old generic error was insufficient.

The [API docs](https://opencode.ai/v2/docs/console/api/inference/) still advertise anonymous free chat, but the official repository records [third-party free-tier restrictions](https://github.com/anomalyco/opencode/issues/49433), and the [third-party support request](https://github.com/anomalyco/opencode/issues/42500) was closed as not planned. These reports are evidence of service/documentation inconsistency, not a guarantee that every 403 has the same cause. Mobie now distinguishes recognized free-tier client restrictions, security-service blocks, authentication requirements and unclassified denials without displaying response bodies. Anonymous access is labeled experimental.

A genuine [OpenCode server](https://opencode.ai/docs/server/) supports custom clients, but would require a separately validated backend, restricted agent permissions, authentication, and revised data disclosures. [Current genuine-client failures](https://github.com/anomalyco/opencode/issues/56397) mean neither that server nor simply adding an API key is a proven repair for the free tier. No client impersonation, fabricated OpenCode sessions or access-control bypass was added. The direct anonymous feature remains unverified and is currently blocked in the live probe.
