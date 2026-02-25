#!/usr/bin/env bash
set -euo pipefail

# CI Gates Orchestrator
# Runs all CI gate scripts in sequence with aggregate pass/fail reporting.
# Gates:
#   1. Unit tests             (always)
#   2. Lint                   (always)
#   3. Compose stability      (always, requires prior assembleRelease)
#   4. APK size               (always, requires prior assembleRelease)
#   5. Coordinator coverage   (always, Kover on :feature:dashboard)
#   6. Clean + incremental build time (--full flag only)
#   7. Benchmarks             (only if benchmark JSON found)
#   8. Baseline profile in APK (only if release APK exists)
# Exit 0 = all gates pass, Exit 1 = any gate fails.

PREFIX="[ci-gates]"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --- Parse flags ---
FULL_MODE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --full)
      FULL_MODE=true
      shift
      ;;
    *)
      echo "${PREFIX} Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

# Gate results array for summary table
declare -a GATE_NAMES=()
declare -a GATE_RESULTS=()

# --- run_gate function ---
# Args: gate_name command [args...]
run_gate() {
  local gate_name="$1"
  shift
  local cmd=("$@")

  GATE_NAMES+=("${gate_name}")
  echo ""
  echo "================================================================"
  echo "${PREFIX} Gate: ${gate_name}"
  echo "================================================================"

  local exit_code=0
  "${cmd[@]}" || exit_code=$?

  if [[ ${exit_code} -eq 0 ]]; then
    GATE_RESULTS+=("PASS")
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "${PREFIX} >>> ${gate_name}: PASS"
  else
    GATE_RESULTS+=("FAIL")
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "${PREFIX} >>> ${gate_name}: FAIL (exit ${exit_code})"
  fi

  return 0  # Don't let gate failure abort orchestrator
}

# --- skip_gate function ---
skip_gate() {
  local gate_name="$1"
  local reason="$2"

  GATE_NAMES+=("${gate_name}")
  GATE_RESULTS+=("SKIP")
  SKIP_COUNT=$((SKIP_COUNT + 1))
  echo ""
  echo "${PREFIX} Gate: ${gate_name} — SKIPPED (${reason})"
}

cd "${PROJECT_DIR}"

echo "${PREFIX} Starting CI gates..."
echo "${PREFIX} Project: ${PROJECT_DIR}"
echo "${PREFIX} Full mode: ${FULL_MODE}"

# ========================================
# Gate 1: Unit tests
# ========================================
run_gate "Unit tests" ./gradlew test --console=plain

# ========================================
# Gate 2: Lint
# ========================================
run_gate "Lint" ./gradlew lintDebug --console=plain

# ========================================
# Gate 3: Compose stability
# ========================================
run_gate "Compose stability" "${SCRIPT_DIR}/check-compose-stability.sh"

# ========================================
# Gate 4: APK size
# ========================================
run_gate "APK size" "${SCRIPT_DIR}/check-apk-size.sh"

# ========================================
# Gate 5: Coordinator coverage
# ========================================
run_gate "Coordinator coverage" ./gradlew :feature:dashboard:koverVerify --console=plain

# ========================================
# Gate 6: Clean + incremental build time (--full only)
# ========================================
if [[ "${FULL_MODE}" == true ]]; then
  run_gate "Build time" "${SCRIPT_DIR}/check-build-time.sh"
else
  skip_gate "Build time" "requires --full flag"
fi

# ========================================
# Gate 7: Benchmarks (only if JSON found)
# ========================================
BENCHMARK_JSON=""
BENCHMARK_BASE="${PROJECT_DIR}/benchmark/build/outputs/connected_android_test_additional_output/benchmarkAndroidTest/connected"
if [[ -d "${BENCHMARK_BASE}" ]]; then
  FOUND=$(find "${BENCHMARK_BASE}" -maxdepth 2 -name "*benchmarkData.json" -print -quit 2>/dev/null || true)
  if [[ -n "${FOUND}" ]]; then
    BENCHMARK_JSON="${FOUND}"
  fi
fi

if [[ -n "${BENCHMARK_JSON}" ]]; then
  run_gate "Benchmarks" "${SCRIPT_DIR}/check-benchmark.sh" "${BENCHMARK_JSON}"
else
  skip_gate "Benchmarks" "no benchmark JSON found (run connectedAndroidTest on device first)"
fi

# ========================================
# Gate 8: Baseline profile in APK
# ========================================
RELEASE_APK="${PROJECT_DIR}/app/build/outputs/apk/release/app-release.apk"
if [[ -f "${RELEASE_APK}" ]]; then
  # Check for baseline profile inside the APK
  if unzip -l "${RELEASE_APK}" 2>/dev/null | grep -q 'baseline'; then
    echo ""
    echo "================================================================"
    echo "${PREFIX} Gate: Baseline profile in APK"
    echo "================================================================"
    echo "${PREFIX} Found baseline profile in release APK"
    GATE_NAMES+=("Baseline profile in APK")
    GATE_RESULTS+=("PASS")
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "${PREFIX} >>> Baseline profile in APK: PASS"
  else
    echo ""
    echo "================================================================"
    echo "${PREFIX} Gate: Baseline profile in APK"
    echo "================================================================"
    echo "${PREFIX} ERROR: No baseline profile found in release APK"
    GATE_NAMES+=("Baseline profile in APK")
    GATE_RESULTS+=("FAIL")
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "${PREFIX} >>> Baseline profile in APK: FAIL"
  fi
else
  skip_gate "Baseline profile in APK" "no release APK found (run assembleRelease first)"
fi

# ========================================
# Summary
# ========================================
echo ""
echo "================================================================"
echo "${PREFIX} SUMMARY"
echo "================================================================"
echo ""

printf "%-30s %s\n" "Gate" "Result"
printf "%-30s %s\n" "------------------------------" "------"
for i in "${!GATE_NAMES[@]}"; do
  printf "%-30s %s\n" "${GATE_NAMES[$i]}" "${GATE_RESULTS[$i]}"
done

echo ""
echo "${PREFIX} Passed: ${PASS_COUNT}  Failed: ${FAIL_COUNT}  Skipped: ${SKIP_COUNT}"
echo ""

if [[ ${FAIL_COUNT} -gt 0 ]]; then
  echo "${PREFIX} FAILED - ${FAIL_COUNT} gate(s) did not pass."
  exit 1
else
  echo "${PREFIX} All gates passed."
  exit 0
fi
