# UI and history integration candidate

This candidate starts from main 382b61c08f5da6f3f8f20c99b9f92d7418956571. It merges the chat-home branch and ports bounded local document attachments from the older chat-first branch while retaining the newer design, navigation and ordered history writes.

The native runtime package, runtime dependency pins, build configuration and memory/backend policies are unchanged from main. This candidate excludes perf/gpu-acceleration, new GPU Apply controls and all proposed native ownership, dispatch, reset and cleanup changes. It needs no new network destination or app permission. Document contents enter only the local prompt and app-private history.

Attachment handling enforces text/type/size/context limits, reads content URIs on IO, rejects provider failures visibly, preserves attachments in history and guards asynchronous results against changed conversations or sent drafts. Filename chips use a bounded width so their text remains visible in the horizontal row. Saved startup preferences are read before the home opens.

Validation covers the existing unit/lint/debug/emulator suite, attachment policy and legacy-history compatibility, real content-provider reads/failures, and a MainActivity workflow that opens a second-model saved chat, renames it, recreates the activity, reopens it and deletes only that session. Exact commands, candidate SHA, results and logs are in the portfolio worker report.

This is a UI integration, not a claim that every inherited runtime behavior is qualified. Main's stall guard still queues JNI cleanup through Dispatchers.Default with cancellable workers. Under complete dispatcher starvation a cancellation request can expire before its delegate starts, as reproduced by the independent runtime review. Main's native cleanup behavior is retained unchanged. Fixes to those native paths remain on the separate hardware-blocked GPU integration branch until physical ARM64 qualification is available. The GPU branch's historical phone measurements do not validate this candidate or its pending native fixes.
