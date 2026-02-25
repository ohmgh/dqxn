#!/usr/bin/env bash
set -euo pipefail

# Build Time Gate (NF35)
# Runs clean build (threshold: < 120s) and incremental build (threshold: < 15s).
# Exit 0 = both pass, Exit 1 = either threshold exceeded.
#
# WARNING: This script actually runs Gradle builds. It is a longer-running CI step
# and should be called explicitly, not as part of every commit.

PREFIX="[build-time]"

# Parse --project-dir argument
PROJECT_DIR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-dir)
      PROJECT_DIR="$2"
      shift 2
      ;;
    --project-dir=*)
      PROJECT_DIR="${1#*=}"
      shift
      ;;
    *)
      echo "${PREFIX} Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# Default to parent directory of this script
if [[ -z "${PROJECT_DIR}" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
fi

GRADLEW="${PROJECT_DIR}/gradlew"
if [[ ! -x "${GRADLEW}" ]]; then
  echo "${PREFIX} ERROR: gradlew not found or not executable at ${GRADLEW}" >&2
  exit 1
fi

CLEAN_THRESHOLD=120
INCREMENTAL_THRESHOLD=15
GATE_FAILED=0

# Touch target for incremental build invalidation
TOUCH_TARGET="${PROJECT_DIR}/app/src/main/kotlin/app/dqxn/android/DqxnApplication.kt"
if [[ ! -f "${TOUCH_TARGET}" ]]; then
  echo "${PREFIX} [WARN] Touch target not found: ${TOUCH_TARGET}"
  echo "${PREFIX} [WARN] Will use app/src/main as fallback touch target"
  TOUCH_TARGET="${PROJECT_DIR}/app/src/main"
fi

echo "${PREFIX} Project directory: ${PROJECT_DIR}"
echo "${PREFIX} Starting build time gates..."
echo ""

# --- Clean build gate ---
echo "${PREFIX} Running clean build (threshold: ${CLEAN_THRESHOLD}s)..."

cd "${PROJECT_DIR}"

# Clean first
"${GRADLEW}" clean --console=plain -q 2>/dev/null

# Time the clean build
CLEAN_START=$(date +%s)
"${GRADLEW}" assembleDebug --console=plain --no-build-cache 2>&1 | tail -1
CLEAN_END=$(date +%s)

CLEAN_DURATION=$((CLEAN_END - CLEAN_START))

echo "${PREFIX} Clean build: ${CLEAN_DURATION}s (threshold: ${CLEAN_THRESHOLD}s)"

if [[ ${CLEAN_DURATION} -ge ${CLEAN_THRESHOLD} ]]; then
  echo "${PREFIX} [FAIL] Clean build ${CLEAN_DURATION}s >= ${CLEAN_THRESHOLD}s"
  GATE_FAILED=1
else
  echo "${PREFIX} [PASS] Clean build ${CLEAN_DURATION}s < ${CLEAN_THRESHOLD}s"
fi

echo ""

# --- Incremental build gate ---
echo "${PREFIX} Running incremental build (threshold: ${INCREMENTAL_THRESHOLD}s)..."

# Invalidate one source file to trigger incremental compilation
# touch updates mtime without modifying content -- idempotent and crash-safe
touch "${TOUCH_TARGET}"

# Time the incremental build (with build cache, no clean)
INCR_START=$(date +%s)
"${GRADLEW}" assembleDebug --console=plain 2>&1 | tail -1
INCR_END=$(date +%s)

INCR_DURATION=$((INCR_END - INCR_START))

echo "${PREFIX} Incremental build: ${INCR_DURATION}s (threshold: ${INCREMENTAL_THRESHOLD}s)"

if [[ ${INCR_DURATION} -ge ${INCREMENTAL_THRESHOLD} ]]; then
  echo "${PREFIX} [FAIL] Incremental build ${INCR_DURATION}s >= ${INCREMENTAL_THRESHOLD}s"
  GATE_FAILED=1
else
  echo "${PREFIX} [PASS] Incremental build ${INCR_DURATION}s < ${INCREMENTAL_THRESHOLD}s"
fi

echo ""

# --- Summary ---
if [[ ${GATE_FAILED} -eq 1 ]]; then
  echo "${PREFIX} FAILED - Build time gates not met."
  exit 1
else
  echo "${PREFIX} All build time gates passed."
  exit 0
fi
