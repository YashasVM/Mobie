# Streaming-channel allocation audit

This is a host JVM microbenchmark, not a phone inference benchmark. It does not establish faster model loading, network transfer, first-token latency, or model tokens/second.

## Implemented

`LiteRtChannelText.split` now returns the original immutable chunk string for singleton channel maps instead of copying it through two allocated StringBuilders. Reasoning classification and multi-channel concatenation are unchanged. Three regression tests cover reference reuse, case-insensitive reasoning classification, and empty singleton values; existing multi-channel tests remain.

## Measured

JDK 17, one million calls per sample, seven alternating baseline/fixed rounds, 100,000 paired warmup calls per chunk size, alternating visible/reasoning singleton maps. Each result is the median. Allocation uses the host ThreadMXBean; a volatile result sink prevents elimination. Inputs are reused. Two independent JVM processes produced:

| Chunk chars | Baseline ns/call, runs 1 / 2 | Fixed ns/call, runs 1 / 2 | Baseline bytes/call | Fixed bytes/call |
|---|---:|---:|---:|---:|
| 8 | 26.92 / 25.28 | 27.43 / 26.38 | 136 | 24 |
| 128 | 40.32 / 45.85 | 28.11 / 27.21 | 400 | 24 |
| 2048 | 492.09 / 471.62 | 27.58 / 28.08 | 4240 | 24 |

The demonstrated gain is fewer allocations and no chunk copy, with lower processing time for larger chunks. The 8-character case is slightly slower in elapsed nanoseconds despite lower allocation. These are tiny absolute costs relative to native generation; no user-visible throughput improvement is promised. Host JVM escape analysis, GC, and allocator behavior differ from Android ART.

## Reproduce

Compile test classes with the repository Gradle build, then run the standalone benchmark class with the main/test Kotlin outputs and pinned Kotlin stdlib on the JVM classpath:

```bash
./gradlew compileDebugUnitTestKotlin
STDLIB=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.3.0" -name '*.jar' | head -1)
java -Xmx512m -cp "app/build/tmp/kotlin-classes/debug:app/build/tmp/kotlin-classes/debugUnitTest:$STDLIB" dev.yashasvm.mobie.core.runtime.LiteRtChannelTextBenchmark 1000000
```

The benchmark is opt-in (a standalone main, not a timing assertion in the unit suite). Its baseline is the previous production splitter. Reflection is used only outside the timed loop to query host-only memory-management APIs without changing Android compile dependencies.

## Rejected download tuning

Separate host Java probes used deterministic cached files, three warmup pairs, and nine alternating trial pairs:

- SHA-256 of 512 MiB: 8 KiB read median 418.558 ms; 64 KiB 412.705 ms. The 1.4% difference was within noise.
- Copy and SHA-256 of 256 MiB, 8 KiB input: RandomAccessFile median 273.235 ms; 64 KiB buffered output 275.042 ms. Buffering was slightly slower.

Neither speculative change was shipped. Existing installed-file fingerprint caching and one-pass transfer hashing already avoid common repeated whole-model reads.

## Larger opportunities requiring additional evidence

- Main-thread chat-history serialization in the current ViewModel can affect responsiveness. Open PRs already change this architecture. Integrate and verify with the selected UI direction rather than introducing a competing rewrite.
- Existing GPU branches require exact-build physical ARM64 validation. Do not infer model speed or compatibility from this synthetic benchmark or an x86 emulator.
