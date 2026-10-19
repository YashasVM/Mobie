# Retained-engine setup: bounded emulator evidence

Source: [GOD MODE checkpoint 9d655f0](https://github.com/YashasVM/Mobie/commit/9d655f06993dc2aa6d2c87434073fbc3b0c19dc1), [successful CI run](https://github.com/YashasVM/Mobie/actions/runs/39282339617), [raw artifact](https://github.com/YashasVM/Mobie/actions/runs/39282339617/artifacts/12202377852).

The adjacent JSON and CSV are the unmodified exported measurements. Their exact model, app APK, test APK, prompt/history hashes, native settings and emulator fingerprint identify this run. All four CI jobs passed. This checkpoint adds measurement/evidence handling, not another production-code optimization over 99879c5.

## Result

Excluding one warmup per condition, four alternating-order matched pairs gave these medians:

| Native API-path measurement | Full engine reload | Retained engine, replace conversation |
| --- | ---: | ---: |
| Settings setup | 1571.07 ms | 2.16 ms |
| Runtime first nonempty SDK chunk after setup | 2143.50 ms | 1731.50 ms |
| Setup plus runtime first chunk | 3717.07 ms | 1733.71 ms |

The median combined setup-plus-first-chunk time was 53.4% lower in this fixture. This calculation is `1 - median(candidate_combined) / median(baseline_combined)`, not an average of pairwise percentages. Every paired answer hash and decode token count matched. All measured trials and cleanup completed successfully. The full-load condition replaced its Engine object; reconfiguration retained it.

## Scope and limits

- Android 35 x86_64 debug emulator, Qwen3-0.6B INT4, CPU1, context1280, output cap24. This is not an ARM64 phone, GPU test, production release benchmark, or comparison with Google AI Edge Gallery.
- Both paths were preconditioned to the same restored history and prior sampler. The timed baseline is configure plus load on the existing engine, without an extra unload. The candidate changes only conversation settings.
- Fixture hashing and warmups occurred before timing. OS page cache and persistent compiled caches were not cleared or controlled. The result isolates this warm-cache API path; it does not establish cold startup performance.
- UI/frame rendering was not measured. Runtime TTFT begins inside the adapter and ends at its first nonempty SDK chunk; the combined value is not tap-to-pixel latency.
- Each answer contained only two native decode tokens. Do not infer sustained tokens/sec improvement from the short per-trial decode rates. Longer fixed-workload generation and physical thermal measurements are still needed for that claim.
- Four pairs on one shared runner are mechanism evidence, not a population estimate or statistical guarantee. Preserve individual rows and report future regressions honestly.

Phone testing is deferred to the user's later APK trial. The PR remains draft under the existing physical ARM64 runtime validation gate; that does not prevent delivering the code and emulator evidence for review.
