#!/usr/bin/env bash
# Runs one opt-in production-adapter benchmark on a physical ARM64 phone.
# Host requirements: Bash, Python 3.8+, standard POSIX utilities, and Android platform-tools.
# PROMPT_FIXTURE=short-latency (default; latency probe only) or sustained-decode.
# MIN_DECODE_TOKENS overrides the fixture's minimum native decoded tokens (sustained default 64);
# it must not exceed OUTPUT_TOKENS, so sustained runs usually also raise OUTPUT_TOKENS.
set -euo pipefail
if (( $# < 4 || $# > 6 )); then
  echo 'Usage: run-gpu-benchmark.sh SERIAL MODEL_PATH BACKEND OUTPUT_DIR [CPU_THREADS] [REPETITIONS]' >&2
  exit 2
fi
serial=$1
model_path=$2
backend=$3
output_dir=$4
cpu_threads=${5:-}
repetitions=${6:-4}
adb_bin=${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}
mkdir -p "$output_dir"
run_id=$(python3 -c 'import uuid; print(uuid.uuid4())')
printf '%s\n' "$run_id" > "$output_dir/run-id.txt"
args=(-s "$serial")
instrument_args=(-w -r -e class 'dev.yashasvm.mobie.LiteRtGpuBenchmarkTest#measuresProductionBackendOnPhysicalArm64'
  -e gpuBenchmark true -e modelPath "$model_path" -e backend "$backend" -e repetitions "$repetitions")
instrument_args+=(-e outputTokens "${OUTPUT_TOKENS:-64}")
instrument_args+=(-e benchmarkRunId "$run_id")
instrument_args+=(-e thinkingOff "${THINKING_OFF:-false}" -e sustainedSeconds "${SUSTAINED_SECONDS:-0}" -e coolStart "${COOL_START:-false}")
instrument_args+=(-e holdSeconds "${HOLD_SECONDS:-0}")
# Preserve explicit experiment settings in the instrumentation output. The array protects
# local argument boundaries; quote again below because adb shell joins arguments remotely.
instrument_args+=(-e activationType "${ACTIVATION_TYPE:-AUTO}")
instrument_args+=(-e galleryEquivalent "${GALLERY_SAMPLER:-false}")
instrument_args+=(-e cacheState "${CACHE_STATE:-uncontrolled}")
instrument_args+=(-e promptFixture "${PROMPT_FIXTURE:-short-latency}")
if [[ -n "${MIN_DECODE_TOKENS:-}" ]]; then instrument_args+=(-e minDecodeTokens "$MIN_DECODE_TOKENS"); fi
if [[ -n "${CONTEXT_TOKENS:-}" ]]; then instrument_args+=(-e contextTokens "$CONTEXT_TOKENS"); fi
if [[ -n "${SPECULATIVE_DECODING:-}" ]]; then instrument_args+=(-e speculativeDecoding "$SPECULATIVE_DECODING"); fi
if [[ -n "${THINKING_BUDGET:-}" ]]; then instrument_args+=(-e thinkingBudget "$THINKING_BUDGET"); fi
if [[ -n "${BUILD_REVISION:-}" ]]; then instrument_args+=(-e buildRevision "$BUILD_REVISION"); fi

if [[ -n "$cpu_threads" ]]; then
  instrument_args+=(-e cpuThreads "$cpu_threads")
fi
"$adb_bin" "${args[@]}" shell dumpsys thermalservice > "$output_dir/thermal-before.txt"
"$adb_bin" "${args[@]}" logcat -c
remote_command=$(python3 -c 'import shlex, sys; print(shlex.join(sys.argv[1:]))' \
  am instrument "${instrument_args[@]}" dev.yashasvm.mobie.test/androidx.test.runner.AndroidJUnitRunner)
"$adb_bin" "${args[@]}" shell "$remote_command" | tee "$output_dir/instrumentation.txt"
"$adb_bin" "${args[@]}" logcat -d -s native:V litert:V tflite:V MobieCompute:V MobieGpuBenchmark:V MobieGpuSustained:V AndroidRuntime:E \
  > "$output_dir/native-logcat.txt"
"$adb_bin" "${args[@]}" exec-out run-as dev.yashasvm.mobie cat files/gpu-benchmark.jsonl \
  > "$output_dir/cumulative-results.jsonl"
"$adb_bin" "${args[@]}" shell dumpsys thermalservice > "$output_dir/thermal-after.txt"
if ! grep -Fq 'OK (1 test)' "$output_dir/instrumentation.txt"; then
  echo "Benchmark failed; inspect $output_dir/instrumentation.txt and native-logcat.txt" >&2
  exit 1
fi
