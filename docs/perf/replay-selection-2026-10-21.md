# Replay selection: avoid processing unused old history

The replay selector previously normalized and grouped the entire saved transcript before selecting
its newest bounded complete turns. For histories longer than 64 messages, it now groups turns lazily
from newest to oldest, stopping at the
same existing replay-budget boundary. Message order, whitespace normalization, interrupted-turn
exclusion, image handling, and token/context budgets are unchanged.

## Eliminated work, not an inference-speed benchmark

A deterministic test supplies 100,000 alternating user/assistant messages through a counting list.
At the default context, the result remains the newest 20 messages, while selection reads at most
22 messages (the retained turns plus the next turn that exceeds the budget). The old forward pass
necessarily read all 100,000 messages. This reduces restore-selection work and temporary message/
turn allocations for long histories with a usable recent suffix; it does not reduce model prefill
or decode work and establishes no Android latency or tokens/second speedup.

The scan is not universally bounded: assistant-only prefixes, oversized turns, or long sequences of
interrupted/incomplete turns may still require reading the full history. The full saved transcript
is retained by the app. No older context is dropped beyond the pre-existing selection policy.

## Short-history safeguard

A preliminary interleaved same-compiler host check showed substantial JIT/GC variation and a
possible few-microsecond penalty for the coroutine iterator on 20-message inputs. Rather than
extrapolate a noisy microbenchmark or ignore a potential regression, histories of 64 messages or
fewer retain the original eager grouping algorithm. Only longer transcripts use lazy traversal.
The threshold is a conservative guard tied to the existing maximum replay message count, not a
claimed device-tuned optimum. Physical app latency still needs measurement.

## Correctness checks

- A frozen pre-change selector is the oracle for 2,000 deterministic randomized histories, including
  blank messages, malformed Unicode, image paths, interrupted turns, varied roles, oversized text,
  and contexts from 512 to 65,536 tokens.
- Explicit regressions cover blank user entries between assistant chunks, consecutive users,
  multiple assistant chunks, image stripping on assistant messages, linked-list input, empty and
  assistant-only histories, and skipping oversized/interrupted newest turns.
- Explicit 63/64/65/66-message cases cover the eager/lazy dispatch boundary.
- Existing history retention, context admission, and UTF-8 counting suites remain applicable.

Full Android CI and exact-build physical ARM64 validation remain required before treating this
runtime-path change as merge-ready. No model, sampler, backend, quality setting, or native library
changes are included.
