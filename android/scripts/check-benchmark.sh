#!/usr/bin/env bash
set -euo pipefail

# Benchmark Result Parser Gate
# Parses macrobenchmark JSON output and checks P50/P95/P99 frame time + startup thresholds.
# Thresholds:
#   P50 frame time:      < 8.0ms   (hard gate)
#   P95 frame time:      < 16.67ms (hard gate, 60fps target — NF1)
#   P95 frame time:      > 12.0ms  triggers WARNING (aspirational target)
#   P99 frame time:      < 16.0ms  (hard gate — NF10)
#   Cold startup median: < 1500.0ms (hard gate)
# Exit 0 = pass, Exit 1 = fail.

PREFIX="[benchmark]"

# --- Argument parsing ---
if [[ $# -lt 1 ]]; then
  echo "${PREFIX} ERROR: Usage: check-benchmark.sh <path-to-benchmarkData.json-or-directory>" >&2
  exit 1
fi

INPUT_PATH="$1"

# If argument is a directory, find the benchmark JSON within it
BENCHMARK_JSON=""
if [[ -d "${INPUT_PATH}" ]]; then
  # Try newer naming convention first
  FOUND=$(find "${INPUT_PATH}" -maxdepth 2 -name "*-benchmarkData.json" -print -quit 2>/dev/null || true)
  if [[ -n "${FOUND}" ]]; then
    BENCHMARK_JSON="${FOUND}"
  else
    # Fallback to plain name
    FOUND=$(find "${INPUT_PATH}" -maxdepth 2 -name "benchmarkData.json" -print -quit 2>/dev/null || true)
    if [[ -n "${FOUND}" ]]; then
      BENCHMARK_JSON="${FOUND}"
    fi
  fi

  if [[ -z "${BENCHMARK_JSON}" ]]; then
    echo "${PREFIX} ERROR: No benchmarkData.json found in directory: ${INPUT_PATH}" >&2
    exit 1
  fi
elif [[ -f "${INPUT_PATH}" ]]; then
  BENCHMARK_JSON="${INPUT_PATH}"
else
  echo "${PREFIX} ERROR: Path does not exist: ${INPUT_PATH}" >&2
  exit 1
fi

echo "${PREFIX} Parsing: ${BENCHMARK_JSON}"
echo ""

# --- Python-based JSON parser and threshold checker ---
python3 -c "
import json
import sys

P50_THRESHOLD = 8.0
P95_HARD_THRESHOLD = 16.67
P95_WARN_THRESHOLD = 12.0
P99_THRESHOLD = 16.0
STARTUP_THRESHOLD = 1500.0

with open('${BENCHMARK_JSON}', 'r') as f:
    data = json.load(f)

benchmarks = data.get('benchmarks', [])
if not benchmarks:
    print('${PREFIX} [WARN] No benchmarks found in JSON')
    sys.exit(0)

failures = []
warnings = []

for bm in benchmarks:
    name = bm.get('name', 'unknown')

    # --- Startup threshold ---
    metrics = bm.get('metrics', {})
    ttid = metrics.get('timeToInitialDisplayMs', {})
    startup_median = ttid.get('median', None)
    if startup_median is not None:
        if startup_median >= STARTUP_THRESHOLD:
            failures.append(f'{name}: cold startup median {startup_median:.1f}ms >= {STARTUP_THRESHOLD:.1f}ms')
        else:
            print(f'${PREFIX} [PASS] {name}: startup median {startup_median:.1f}ms < {STARTUP_THRESHOLD:.1f}ms')

    # --- Frame duration thresholds ---
    # Try sampledMetrics first (newer format), then metrics (older format)
    sampled = bm.get('sampledMetrics', {})
    frame_data = sampled.get('frameDurationCpuMs', None)
    if frame_data is None:
        frame_data = metrics.get('frameDurationCpuMs', None)

    if frame_data is not None:
        p50 = frame_data.get('P50', None)
        p95 = frame_data.get('P95', None)
        p99 = frame_data.get('P99', None)

        if p50 is not None:
            if p50 >= P50_THRESHOLD:
                failures.append(f'{name}: P50 frame time {p50:.2f}ms >= {P50_THRESHOLD:.2f}ms')
            else:
                print(f'${PREFIX} [PASS] {name}: P50 {p50:.2f}ms < {P50_THRESHOLD:.2f}ms')

        if p95 is not None:
            if p95 >= P95_HARD_THRESHOLD:
                failures.append(f'{name}: P95 frame time {p95:.2f}ms >= {P95_HARD_THRESHOLD:.2f}ms')
            elif p95 > P95_WARN_THRESHOLD:
                warnings.append(f'{name}: P95 frame time {p95:.2f}ms > {P95_WARN_THRESHOLD:.2f}ms (aspirational target)')
                print(f'${PREFIX} [WARN] {name}: P95 {p95:.2f}ms > {P95_WARN_THRESHOLD:.2f}ms aspirational target')
            else:
                print(f'${PREFIX} [PASS] {name}: P95 {p95:.2f}ms < {P95_WARN_THRESHOLD:.2f}ms')

        if p99 is not None:
            if p99 >= P99_THRESHOLD:
                failures.append(f'{name}: P99 frame time {p99:.2f}ms >= {P99_THRESHOLD:.2f}ms')
            else:
                print(f'${PREFIX} [PASS] {name}: P99 {p99:.2f}ms < {P99_THRESHOLD:.2f}ms')

print()

for w in warnings:
    print(f'${PREFIX} [WARN] {w}')

if failures:
    for fail in failures:
        print(f'${PREFIX} FAIL: {fail}')
    print()
    print(f'${PREFIX} FAILED - {len(failures)} benchmark gate(s) not met.')
    sys.exit(1)
else:
    print(f'${PREFIX} All benchmark gates passed.')
    sys.exit(0)
"
