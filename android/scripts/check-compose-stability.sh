#!/usr/bin/env bash
set -euo pipefail

# Compose Stability Audit Gate
# Parses Compose compiler *-classes.txt reports for all app-owned modules.
# Fails if any unstable classes found or if :feature:dashboard has > 5 non-skippable composables.
# Exit 0 = pass, Exit 1 = fail.

PREFIX="[compose-stability]"

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

# App-owned modules with Compose compiler enabled
MODULES=(
  "app"
  "feature/dashboard"
  "feature/settings"
  "feature/diagnostics"
  "feature/onboarding"
  "sdk/ui"
  "core/design"
  "pack/essentials"
  "pack/themes"
  "pack/demo"
)

TOTAL_UNSTABLE=0
FAILED_MODULES=()
DASHBOARD_NON_SKIPPABLE=0
DASHBOARD_THRESHOLD=5

echo "${PREFIX} Scanning ${#MODULES[@]} modules for Compose stability..."
echo ""

for MODULE in "${MODULES[@]}"; do
  MODULE_DIR="${PROJECT_DIR}/${MODULE}"
  REPORT_DIR="${MODULE_DIR}/build/compose_compiler"

  # Check if report directory exists
  if [[ ! -d "${REPORT_DIR}" ]]; then
    echo "${PREFIX} [WARN] No report directory: ${MODULE}/build/compose_compiler/ (module may not be built)"
    continue
  fi

  # Find *-classes.txt files
  REPORT_FILES=()
  while IFS= read -r -d '' file; do
    REPORT_FILES+=("$file")
  done < <(find "${REPORT_DIR}" -name "*-classes.txt" -print0 2>/dev/null)

  if [[ ${#REPORT_FILES[@]} -eq 0 ]]; then
    echo "${PREFIX} [WARN] No *-classes.txt reports in ${MODULE}/build/compose_compiler/"
    continue
  fi

  MODULE_UNSTABLE=0
  for REPORT in "${REPORT_FILES[@]}"; do
    # Count lines containing "unstable " (trailing space avoids matching column headers like "unstableParams")
    COUNT=$(grep -c "unstable " "${REPORT}" 2>/dev/null || true)
    MODULE_UNSTABLE=$((MODULE_UNSTABLE + COUNT))
  done

  if [[ ${MODULE_UNSTABLE} -gt 0 ]]; then
    echo "${PREFIX} [FAIL] ${MODULE}: ${MODULE_UNSTABLE} unstable class(es)"
    FAILED_MODULES+=("${MODULE}")
  else
    echo "${PREFIX} [PASS] ${MODULE}: 0 unstable classes"
  fi

  TOTAL_UNSTABLE=$((TOTAL_UNSTABLE + MODULE_UNSTABLE))

  # Non-skippable composable check for :feature:dashboard only
  if [[ "${MODULE}" == "feature/dashboard" ]]; then
    for REPORT in "${REPORT_FILES[@]}"; do
      # Count lines that are "restartable " but NOT "skippable "
      NON_SKIP=$(grep "restartable " "${REPORT}" 2>/dev/null | grep -cv "skippable " 2>/dev/null || true)
      DASHBOARD_NON_SKIPPABLE=$((DASHBOARD_NON_SKIPPABLE + NON_SKIP))
    done
  fi
done

echo ""

# Summary
GATE_FAILED=0

if [[ ${TOTAL_UNSTABLE} -gt 0 ]]; then
  echo "${PREFIX} [FAIL] Total unstable classes: ${TOTAL_UNSTABLE} (threshold: 0)"
  echo "${PREFIX}        Failed modules: ${FAILED_MODULES[*]}"
  GATE_FAILED=1
else
  echo "${PREFIX} [PASS] Total unstable classes: 0"
fi

if [[ ${DASHBOARD_NON_SKIPPABLE} -gt ${DASHBOARD_THRESHOLD} ]]; then
  echo "${PREFIX} [FAIL] :feature:dashboard non-skippable composables: ${DASHBOARD_NON_SKIPPABLE} (threshold: ${DASHBOARD_THRESHOLD})"
  GATE_FAILED=1
else
  echo "${PREFIX} [PASS] :feature:dashboard non-skippable composables: ${DASHBOARD_NON_SKIPPABLE} (threshold: ${DASHBOARD_THRESHOLD})"
fi

echo ""

if [[ ${GATE_FAILED} -eq 1 ]]; then
  echo "${PREFIX} FAILED - Compose stability gates not met."
  exit 1
else
  echo "${PREFIX} All Compose stability gates passed."
  exit 0
fi
