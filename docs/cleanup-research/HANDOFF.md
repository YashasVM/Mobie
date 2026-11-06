# Mobie GPU / speed work: master handoff

Written 2026-10-13 when the Codex 5-hour limit was close. Start here, then read the per-agent handoffs in this folder.

## Where the work lives

- Worktree: `/home/yvm/code/Mobie-gpu`, branch `perf/gpu-acceleration`, based on `ui/terminal-theme` @ b4c2c4d.
- **Everything is uncommitted** (see `git -C /home/yvm/code/Mobie-gpu status`). Don't reset or clean that worktree.
- Research notes: `/home/yvm/code/Mobie-research/`.
- Phone: Galaxy S24 Ultra SM-S928B, serial `RZCY81Q06XR`, Android 16, Snapdragon 8 Gen 3 / Adreno 750. Emulator: `emulator-5554`.
  adb: `~/Android/Sdk/platform-tools/adb`. Only ONE agent may use the phone at a time (benchmarks and installs interfere). UI checks go on the emulator.
- Build: `ANDROID_HOME=/home/yvm/Android/Sdk ./gradlew testDebugUnitTest lintDebug assembleDebug`.

## User goals (from the conversation)

1. GPU inference that actually works, user-selectable CPU / GPU / Auto (+ thread count).
2. Dynamic hardware detection and a per-backend tok/s figure (measured when available, else a labelled estimate), never hard-coded.
3. **Goal loop:** only report "done" once decode speed is **≥2x (ideally 3x)** the shipped baseline, measured like-for-like on the phone.
4. Beat Google AI Edge Gallery on the same phone, and give users more control than Gallery: an "Advanced" panel with every knob that measurably matters.
5. "Free up memory": Android 14+ forbids killing other apps, so the honest version shows the gap, opens Recents, and re-measures on return (agent F).
6. Open as a new PR (user expects #17; GitHub assigns the number). Benchmark table + approximate speedup score in the PR.
7. Subagents: Codex `gpt-6.1-sol`, reasoningEffort **high** (fallback `gpt-6-sol` on capacity errors).

## Results so far (agent A, physical phone)

- **Root cause:** LiteRT-LM **0.16.1**'s GPU delegate rejects the installed Qwen3-0.6B dynamic artifact at `STABLEHLO_COMPOSITE odml.swiglu`. Only 46/916 prefill nodes get delegated, full delegation fails, and Auto falls back to CPU silently. OpenCL itself loads fine.
- **LiteRT-LM 0.18.0** fully delegates the same artifact (916/916 prefill, 830/830 decode on OpenCL).
- Old baseline (Auto + CPU 8 threads as shipped, pre-visible-output harness): median **8.05 decode tok/s**, 12.01 prefill, TTFT 3.63 s, load 3.31 s.
- New harness (0.18.0, same SHA artifact, ThinkingConfig(false), same prompt, 64 visible tokens, thermal 0 throughout):
  - **GPU median 30.09 decode tok/s** (~3.74x vs 8.05 — NOT yet like-for-like), prefill ~182 tok/s, TTFT ~0.98 s, peak PSS ~3.06 GiB.
  - CPU2 6.21, CPU4 7.93. CPU tuning gives nothing so far. CPU6/8 sweep unfinished.
- Mixed INT4 Qwen artifact: GPU works even on 0.16.1 (~22.4 tok/s), CPU4 ~24.1.

## UPDATE after agent handoffs (all per-agent notes written)

- **Fair, like-for-like result (goal met):** matched 0.16.1 Auto+CPU8 baseline (thinking off, same prompt, 64 tokens, warm-up + 3 reps, thermal 0) = **7.65 decode tok/s**, 13.9 prefill, first visible chunk 3.19 s, peak PSS ~1.84 GB. 0.18.0 explicit GPU on the same harness = **30.09 tok/s → 3.93x**. TTFT ~0.98 s (~3.3x faster). Best CPU (4 threads on 0.18.0) = 7.93 (1.04x), so the gain is the GPU, not threads.
- Done by A: fair baseline, PSS breakdown, CPU 2/4/6/8 sweep (4 best = already the default), 0.18.0 LICENSE/NOTICE packaged.
- GPU memory: 4096-context GPU PSS ~3.2 GB (GL mtrack 2.26 GB). One 1024-context probe halved it (~1.6 GB) at the same ~30 tok/s. Repeat this, test 2048, and decide whether to expose context size as a user knob.
- Still TODO for A: 5-min sustained test (harness written, not run), new-default Auto ratio, cancel/unload/backend-switch retest, fp16/speculative trials, Gallery-equivalent run + knob list, on-screen demo, docs/perf/gpu-benchmark.md, re-run full gates (last full pass predates the memory-relief edits).
- B: **none of the 6 review fixes applied** yet (no half-edits).
- F: see handoff-F-memory.md.

## Remaining TODO, in priority order

A — runtime / phone (one agent only):
1. **Fair baseline:** baseline APK (0.16.1), Auto+CPU8, ThinkingConfig(false), same prompt, 64 visible tokens, warm-up + 3 reps, thermal logged. Then compute the real multiplier. This gates the goal loop.
2. Peak PSS 3.06 GiB breakdown (`dumpsys meminfo`: GL mtrack / native / file maps). Trim a duplicate weight copy or oversized KV cache if speed holds.
3. CPU6/8 sweep, then a 5-minute sustained GPU vs CPU test for throttling.
4. From `litertlm-0.18-review.md`: replace the bundled LiteRT-LM LICENSE/NOTICE with the 0.18.0 versions (**release blocker**). Retest cancel mid-generation, unload→reload, CPU↔GPU switch without restart, and GPU cache dir growth. Trial fp16 activation type and speculative decoding; keep each only if it helps with no quality loss.
5. **Never started (queued item was cancelled):** Gallery-equivalent run using `gallery-config.md`, a Mobie vs Gallery column, and exposing every speed-relevant knob via `ComputeOptions` for an Advanced panel (list knobs + safe ranges).
6. Make Auto prefer GPU on this phone, keep the fallback reason visible in the UI, write `docs/perf/gpu-benchmark.md`, and do a visible on-screen chat demo + screenshot.

B — hardware detection / estimate (no phone). Review fixes were in progress; see `handoff-B-hardware-estimate.md`:
1. Estimate is null when cpufreq is unreadable (`DecodeSpeedEstimator.kt:62`).
2. Thread picker allows more threads than cores (`ModelScreen.kt:562`); clamp in the settings layer too.
3. ANGLE wrongly counted as a software GPU (`HardwareProfile.kt:20`).
4. Measurement key lacks runtime version + schema version (`DecodeSpeedStore.kt:24`).
5. No global retention bound (`DecodeSpeedHistory.kt:31`).
6. Calibrate the estimate so the S24U profile contains ~30 tok/s GPU / ~6–8 tok/s CPU for the Qwen3-0.6B artifact.

D — Gallery research: ✅ done, see `gallery-config.md` / `handoff-D-gallery.md`. Key facts: latest Gallery (1.0.20) **also pins LiteRT-LM 0.18.0**, so the upgrade only reaches parity; beating Gallery must come from tuning. Gallery leaves CPU threads unset (native default 4), uses per-model backend order (Gemma 3n defaults to CPU), and its benchmark TTFT is computed rather than observed. Ranked tweaks to try: CPU thread count per topology, separate output/thinking budgets, prefix prefill, per-conversation speculative decoding, artifact-aware context limits. Note: Kotlin `benchmark()` ignores CPU threadCount, so use a real Engine for thread sweeps. Clones are in /tmp and may vanish.
F — free-up-memory UI: see `handoff-F-memory.md`, screenshots in `memory-ui/`.

Then: Advanced panel UI (after A's knob list), independent review of A's and F's changes, full `testDebugUnitTest lintDebug assembleDebug` + `connectedDebugAndroidTest` on the emulator, a final physical-phone run, commit, and the PR (note the 0.16.1→0.18.0 dependency bump, license change, no new network destinations, inference stays on-device).

## How to resume

Spin up fresh `gpt-6.1-sol` (high) subagents with: this file, the relevant per-agent handoff, AGENTS.md, and the ownership split (A: core/runtime, data, manifest, gradle, phone; B: core/device + compute panel; F: memory files + small hooks). Keep only one agent on the phone.

- F: 387 unit tests pass; one emulator navigation-test failure is documented in handoff-F-memory.md and must be fixed before the PR.

## Status at 5a765cb (2026-10-13, after Codex → Claude Haiku switch)

- Draft PR **#18** (https://github.com/YashasVM/Mobie/pull/18), branch perf/gpu-acceleration, all work committed and pushed. A 30-minute checkpoint schedule commits and pushes new changes.
- Code-only work is done: Advanced panel, estimate unification, close-before-create reset, GPU memory admission, all review fixes, Flow transparency fix.
- Gates at 5a765cb: 477 unit tests pass, lint 0 errors, assemble OK. Emulator: 47 passed, 0 failed, 7 skipped.
- Phone results: `phone-P-results.md`, plus `docs/perf/gpu-benchmark.md` in the repo.

### Next phone session (rebuild from current HEAD first; the stable worktree /home/yvm/code/Mobie-phone is at old 2242e75, so update it with `git -C /home/yvm/code/Mobie-phone checkout --detach <HEAD>`)
1. Reset loop on GPU (5 min): confirm peak PSS stays near ~3.1 GiB, not 5.6 GiB, and that TTFT after reset is ~1 s, not ~13 s.
2. CPU4 5-minute control.
3. Refused reload keeps the old engine; load timeout behaviour on a real GPU init; per-backend context.
4. Release (R8) APK runtime check.
5. On-screen chat demo and screenshots of the live compute panel and Advanced panel. The emulator has no model, so live-*.png are stale.
Then mark #18 ready for review.
