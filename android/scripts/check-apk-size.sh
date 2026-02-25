#!/usr/bin/env bash
set -euo pipefail

# APK Size Gate
# Measures release APK file size and enforces thresholds.
# Hard fail: >= 30MB. Warning: > 25MB.
# Exit 0 = pass, Exit 1 = fail.

PREFIX="[apk-size]"

# Parse --apk-path argument
APK_PATH=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk-path)
      APK_PATH="$2"
      shift 2
      ;;
    --apk-path=*)
      APK_PATH="${1#*=}"
      shift
      ;;
    *)
      echo "${PREFIX} Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# Auto-discover APK if not provided
if [[ -z "${APK_PATH}" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
  APK_DIR="${PROJECT_DIR}/app/build/outputs/apk/release"

  if [[ -f "${APK_DIR}/app-release.apk" ]]; then
    APK_PATH="${APK_DIR}/app-release.apk"
  elif [[ -f "${APK_DIR}/app-release-unsigned.apk" ]]; then
    APK_PATH="${APK_DIR}/app-release-unsigned.apk"
  else
    echo "${PREFIX} ERROR: No release APK found in ${APK_DIR}/" >&2
    echo "${PREFIX} Build with: ./gradlew assembleRelease" >&2
    exit 1
  fi
fi

if [[ ! -f "${APK_PATH}" ]]; then
  echo "${PREFIX} ERROR: APK not found at ${APK_PATH}" >&2
  exit 1
fi

# Get file size in bytes (cross-platform)
if [[ "$(uname)" == "Darwin" ]]; then
  SIZE_BYTES=$(stat -f%z "${APK_PATH}")
else
  SIZE_BYTES=$(stat --format=%s "${APK_PATH}")
fi

# Convert to MB (integer division)
SIZE_MB=$((SIZE_BYTES / 1048576))

THRESHOLD_FAIL=30
THRESHOLD_WARN=25

echo "${PREFIX} APK: $(basename "${APK_PATH}")"
echo "${PREFIX} APK size: ${SIZE_MB}MB (${SIZE_BYTES} bytes)"

if [[ ${SIZE_MB} -ge ${THRESHOLD_FAIL} ]]; then
  echo "${PREFIX} [FAIL] APK size ${SIZE_MB}MB >= ${THRESHOLD_FAIL}MB threshold"
  exit 1
elif [[ ${SIZE_MB} -gt ${THRESHOLD_WARN} ]]; then
  echo "${PREFIX} [WARN] APK size ${SIZE_MB}MB > ${THRESHOLD_WARN}MB warning threshold"
  echo "${PREFIX} APK size gate passed (with warning)."
  exit 0
else
  echo "${PREFIX} [PASS] APK size ${SIZE_MB}MB < ${THRESHOLD_WARN}MB"
  echo "${PREFIX} APK size gate passed."
  exit 0
fi
