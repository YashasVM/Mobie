#!/usr/bin/env bash
# Explicitly selected physical phone only. Run from any directory inside this checkout.
set -euo pipefail

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the adb serial of a physical ARM64 phone}"
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd -- "$repo_dir"
for tool in adb curl sha256sum python3; do
    command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done
adb_phone=(adb -s "$ANDROID_SERIAL")
echo "Checking selected phone $ANDROID_SERIAL"
"${adb_phone[@]}" get-state
qemu="$("${adb_phone[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
abi="$("${adb_phone[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$ANDROID_SERIAL" == emulator-* || "$qemu" == 1 || "$abi" != arm64-v8a ]]; then
    echo "Refusing benchmark: selected device must be a physical arm64-v8a phone (qemu=$qemu, ABI=$abi)." >&2
    exit 1
fi

repetitions="${REPETITIONS:-3}"
rounds="${ROUNDS:-2}"
output_tokens="${OUTPUT_TOKENS:-256}"
with_vision="${WITH_VISION:-false}"
cool_start="${COOL_START:-false}"
[[ "$repetitions" =~ ^[0-9]+$ && "$rounds" =~ ^[0-9]+$ && "$output_tokens" =~ ^[0-9]+$ ]] || { echo "Counts must be integers" >&2; exit 1; }
[[ "$with_vision" =~ ^(true|false)$ && "$cool_start" =~ ^(true|false)$ ]] || { echo "WITH_VISION/COOL_START must be true or false" >&2; exit 1; }
# Prefix with 10# so leading zeros do not select shell octal arithmetic.
(( 10#$repetitions >= 1 && 10#$repetitions <= 20 && 10#$rounds >= 2 && 10#$rounds <= 20 && 10#$rounds % 2 == 0 && 10#$output_tokens >= 1 && 10#$output_tokens <= 1024 )) || { echo "Use REPETITIONS=1..20, even ROUNDS=2..20, OUTPUT_TOKENS=1..1024" >&2; exit 1; }

cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/mobie-bench"
mkdir -p -- "$cache_dir"
model_name=gemma-4-E2B-it.litertlm
model_file="$cache_dir/$model_name"
model_sha=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
model_url=https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/41c5569623a7c189d6eec3e5225260c58cedbce7/gemma-4-E2B-it.litertlm
verify_model() { printf '%s  %s\n' "$model_sha" "$1" | sha256sum --check --status; }
if [[ ! -f "$model_file" ]] || ! verify_model "$model_file"; then
    if [[ -e "$model_file" ]]; then
        echo "Cached model failed verification. Remove $model_file and retry." >&2
        exit 1
    fi
    if [[ -f "$model_file.part" ]] && verify_model "$model_file.part"; then
        echo "Finalizing an already verified partial download"
    else
        echo "Downloading pinned artifact with resume to $model_file.part"
        curl --fail --location --retry 3 --continue-at - --output "$model_file.part" "$model_url"
    fi
    verify_model "$model_file.part" || { echo "Model checksum failed; remove $model_file.part before retrying." >&2; exit 1; }
    mv -- "$model_file.part" "$model_file"
fi
echo "Verified model SHA-256 $model_sha"
echo "Building debug app and instrumentation APKs"
./gradlew --console=plain assembleDebug assembleDebugAndroidTest
echo "Installing both APKs on $ANDROID_SERIAL"
"${adb_phone[@]}" install -r app/build/outputs/apk/debug/app-debug.apk
"${adb_phone[@]}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

relative_model="models/god-mode/$model_name"
remote_model="/data/local/tmp/mobie-god-mode-$model_name"
echo "Copying verified artifact into app-private files/$relative_model"
"${adb_phone[@]}" push "$model_file" "$remote_model"
"${adb_phone[@]}" shell run-as dev.yashasvm.mobie mkdir -p files/models/god-mode
"${adb_phone[@]}" shell run-as dev.yashasvm.mobie cp "$remote_model" "files/$relative_model.part"
"${adb_phone[@]}" shell run-as dev.yashasvm.mobie mv "files/$relative_model.part" "files/$relative_model"
"${adb_phone[@]}" shell rm -f "$remote_model"

result_dir="${RESULT_DIR:-$repo_dir/build/god-mode-benchmark/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p -- "$result_dir"
revision="$(git rev-parse HEAD)"
echo "Running $rounds rounds, $repetitions repetitions per prompt, output cap $output_tokens; vision=$with_vision; coolStart=$cool_start"
# Clear old exports so a crash cannot resurrect evidence from a previous run.
"${adb_phone[@]}" shell rm -f /sdcard/mobie-god-mode-benchmark.json /sdcard/mobie-god-mode-benchmark.csv
set +e
"${adb_phone[@]}" shell am instrument -w \
    -e class dev.yashasvm.mobie.GodModeSpeedBenchmarkTest \
    -e godModeBenchmark true -e modelPath "$relative_model" -e buildRevision "$revision" \
    -e repetitions "$repetitions" -e rounds "$rounds" -e outputTokens "$output_tokens" \
    -e withVision "$with_vision" -e coolStart "$cool_start" \
    dev.yashasvm.mobie.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "$result_dir/instrumentation.txt"
instrument_status=$?
set -e
echo "Pulling exported JSON and CSV into $result_dir"
"${adb_phone[@]}" pull /sdcard/mobie-god-mode-benchmark.json "$result_dir/mobie-god-mode-benchmark.json"
"${adb_phone[@]}" pull /sdcard/mobie-god-mode-benchmark.csv "$result_dir/mobie-god-mode-benchmark.csv"
python3 scripts/perf/summarize_god_mode.py "$result_dir/mobie-god-mode-benchmark.json" | tee "$result_dir/summary.md"
if (( instrument_status != 0 )) || ! grep -Eq '^OK \([0-9]+ test' "$result_dir/instrumentation.txt"; then
    echo "Instrumentation did not report success; inspect $result_dir/instrumentation.txt" >&2
    exit 1
fi
echo "Phone comparison saved to $result_dir"
