# Tiered Validation Pipeline

Agentic development validation workflow for DQXN. Each tier provides faster feedback than the next, enabling Claude Code to verify changes incrementally without waiting for full builds. Fulfills requirement F13.9.

## Tiers

### Tier 1: Compile Check (~8s)

Catches type errors, missing imports, unresolved references. Fastest feedback loop.

```bash
./gradlew :module:compileDebugKotlin --console=plain
```

**Examples:**
```bash
# Single module
./gradlew :pack:essentials:compileDebugKotlin --console=plain

# App module (includes debug source set)
./gradlew :app:compileDebugKotlin --console=plain

# Release compilation path
./gradlew :app:compileReleaseKotlin --console=plain
```

**When to use:** After every code change. First validation step before anything else.

---

### Tier 2: Fast Tests (~12s)

Unit tests tagged `@Tag("fast")` -- pure logic, no Compose, no I/O.

```bash
./gradlew :module:fastTest --console=plain
```

**Examples:**
```bash
./gradlew :sdk:contracts:fastTest --console=plain
./gradlew :core:thermal:fastTest --console=plain
```

**When to use:** After modifying logic in a single module. Catches regressions in core algorithms.

---

### Tier 3: Full Module Tests (~30s)

All unit tests for a module including Compose rule tests and integration tests.

```bash
./gradlew :module:testDebugUnitTest --console=plain
```

**Examples:**
```bash
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :core:agentic:testDebugUnitTest --console=plain
./gradlew :sdk:observability:testDebugUnitTest --console=plain
```

**When to use:** After completing a feature within a module. Validates Compose interactions, Hilt graph resolution, flow behavior.

---

### Tier 4: Dependent Module Tests (~60s)

All tests across all modules. Catches cross-module regressions.

```bash
./gradlew test --console=plain
```

**When to use:** Before committing changes that affect SDK contracts or shared types. Validates that downstream modules still compile and pass.

---

### Tier 5: On-Device Smoke (~30s)

Deploy to device/emulator and verify agentic framework responds.

```bash
./gradlew :app:installDebug --console=plain && \
  adb shell content call \
    --uri content://app.dqxn.android.debug.agentic \
    --method ping
```

**When to use:** After changes to DI graph, ContentProvider registration, or Activity lifecycle. Validates that the app starts and the agentic transport is operational.

---

### Tier 6: Full Suite

Complete CI gate -- debug build, all tests, lint checks.

```bash
./gradlew assembleDebug test lintDebug --console=plain
```

**When to use:** Before finalizing a plan or phase. Full confidence gate.

---

## Agentic Verification Commands

All commands use the response-file protocol: the `call` returns a Bundle with a `filePath` field pointing to a temp JSON file. Read the file to get the actual response.

### Discovery

```bash
# List all registered commands
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method list-commands

# Read response
adb shell cat $(adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method list-commands | grep -oP 'filePath=\K[^ ]+')
```

### Diagnostics

```bash
# Full semantics tree dump
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method dump-semantics

# Filtered semantics query (by test tag)
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method query-semantics \
  --extra tag:s:dashboard_grid

# Widget health snapshot
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method dump-health

# Frame metrics and per-widget draw times
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method get-metrics

# Last crash evidence
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method diagnose-crash

# Performance summary (frame histogram, jank stats)
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method diagnose-performance
```

### Registry Inspection

```bash
# Registered widget type IDs
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method list-widgets

# Registered data provider source IDs
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method list-providers

# Registered theme IDs grouped by pack
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method list-themes
```

### Testing Utilities

```bash
# Trigger synthetic anomaly for diagnostic capture testing
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method trigger-anomaly \
  --extra reason:s:manual_test

# Capture diagnostic snapshot with custom reason
adb shell content call \
  --uri content://app.dqxn.android.debug.agentic \
  --method capture-snapshot \
  --extra reason:s:pre_release_check
```

### Reading Responses

All command responses are written to temporary files in the app's cache directory. The Bundle returned by `content call` contains the file path:

```bash
# The returned Bundle looks like:
# Bundle[{filePath=/data/user/0/app.dqxn.android/cache/agentic_resp_xxxxx.json, ...}]

# To read the response content:
adb shell cat <filePath>
```

**Note:** Response files are auto-cleaned on next call. Do not cache file paths.

---

## Tier Selection Guide

| Change Type | Minimum Tier | Rationale |
|---|---|---|
| Fix typo in string | Tier 1 | Compile check catches unresolved refs |
| Modify data class field | Tier 4 | Cross-module impact |
| Add new widget renderer | Tier 3 | Module-scoped with Compose |
| Change SDK contract | Tier 4 | All downstream modules affected |
| Modify Hilt module | Tier 5 | DI graph resolution needs runtime |
| Add new handler | Tier 3 | Handler + KSP generation |
| Pre-commit gate | Tier 6 | Full confidence |
| Quick iteration loop | Tier 1 + Tier 2 | Fastest useful feedback |
