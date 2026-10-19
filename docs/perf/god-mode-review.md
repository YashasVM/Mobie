# GOD MODE review and regression checkpoint

An independent read-only native review examined retained-engine lifecycle changes, chat navigation ownership, performance hints, and single-text SDK rendering. The following lifecycle issues were found and corrected:

1. Chat-home lookup claimed navigation ownership after disk IO, allowing older requests to override newer selections. Request ownership is now reserved at invocation, and stale results/errors/completion callbacks cannot publish.
2. A new-chat session write could finish after a model switch and open the old path under the new model's metadata. The original selection revision now survives asynchronous writes and opening, including A → B → A transitions.
3. Warm history reset called native close/create without a watchdog bound. Reset now shares bounded fail-closed handling with conversation reconfiguration.
4. Caller cancellation during model load could leave detached native work without marking recovery required. Cancellation now quarantines the adapter until unload, while ordinary returned admission refusal still preserves a valid previous engine.
5. A failed obsolete unload could overwrite a successor operation's UI error state. Error publication now checks operation ownership without changing native quarantine or cancellation.

Regression coverage includes production coroutine-coordination tests for reordered lookups, stale callbacks, explicit selection, startup failure/empty results, cancellation and delayed session writes; reset tests for timeout/cancellation/returned and thrown failures, successful retention and recovery; and load-cancellation/admission-refusal tests. These helper-level coroutine tests are not a full Android ViewModel/native-driver integration harness.

The review also checked two narrowly scoped streaming changes:

- Unsupported/no-op performance hint sessions no longer read the clock per chunk. Broken optional report sessions stop retrying/logging after the first ordinary platform failure, while normal sessions, cancellation and ownership/close semantics are preserved. Deterministic call-count tests verify the removed work.
- A single SDK Content.Text fallback returns its immutable string directly. Pinned LiteRT-LM 0.18.0 bytecode confirms this preserves rendering while skipping Contents.joinToString's builder/result copy. Channel precedence, reasoning-only suppression and mixed-content rendering remain unchanged; reference-identity and representative legacy-equivalence Android instrumentation tests cover them. These tests use the actual pinned SDK on Android; its newer classfile version cannot be loaded in the JDK 17 host unit-test VM.

No unresolved blocker was found in the reviewed fixes. This is not exhaustive proof. Native ARM64/GPU validation and sustained-generation/thermal comparisons remain deferred to physical-device testing. The separate emulator report documents the measured warm settings-update benefit; the streaming micro-optimizations do not have a claimed native tokens/sec improvement.

Use exact-head CI results in the PR for build/test status, rather than interpreting this source review as a passing build.
