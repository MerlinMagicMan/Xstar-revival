#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_root="$repo_root/software/android-app"
bridge_root="$repo_root/software/android-autel-bridge"
artifact_root="$repo_root/artifacts/bench"
gradle_bin="${GRADLE_BIN:-gradle}"

if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required" >&2
  exit 2
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to a Java 17 installation" >&2
  exit 2
fi

python3 "$bridge_root/tools/verify_read_only_binding.py"

(
  cd "$android_root"
  "$gradle_bin" \
    :autelBridge:verifyAutelSdkAar \
    :appCore:test \
    :app:testDebugUnitTest \
    :app:lintDebug \
    :app:assembleDebug
)

source_apk="$android_root/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$source_apk" ]]; then
  echo "Bench APK was not generated at $source_apk" >&2
  exit 3
fi

mkdir -p "$artifact_root"
bench_apk="$artifact_root/xstar-ground-station-v0.3.0-bench.1-debug.apk"
install -m 0644 "$source_apk" "$bench_apk"
shasum -a 256 "$bench_apk" > "$bench_apk.sha256"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}"
apksigner="$(find "$sdk_root/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n 1)"
if [[ -n "$apksigner" ]]; then
  "$apksigner" verify --verbose "$bench_apk"
fi

if [[ -z "${AUTEL_APP_KEY:-}" ]]; then
  echo "NOTICE: the official receive-only bridge is bundled, but live SDK authorization requires rebuilding with AUTEL_APP_KEY."
fi
echo "Bench APK: $bench_apk"
echo "SHA-256: $(awk '{print $1}' "$bench_apk.sha256")"
