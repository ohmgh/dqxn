---
phase: 02-sdk-contracts-common
plan: 01
subsystem: sdk
tags: [kotlin, coroutines, hilt, state-machine, result-type, jqwik, junit5]

# Dependency graph
requires:
  - phase: 01-build-system
    provides: Convention plugins (dqxn.android.library, dqxn.android.hilt, dqxn.android.test), version catalog, module stubs
provides:
  - AppResult<T> sealed result type with 7 extension functions
  - AppError sealed hierarchy (7 variants: Network, Bluetooth, Permission, Device, Database, Pack, Unknown)
  - PermissionKind enum (6 permission types)
  - ConnectionStateMachine with 6 states, 7 events, retry logic, exponential backoff
  - Dispatcher qualifiers (@IoDispatcher, @DefaultDispatcher, @MainDispatcher, @ApplicationScope)
  - DispatcherModule (Hilt @SingletonComponent)
  - Flow extensions (throttleLatest, catchAndLog)
affects: [02-sdk-contracts-common, 03-sdk-observability-analytics-ui, 05-core-infrastructure, 07-dashboard-shell, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: [kotlinx-coroutines-core, kotlinx-coroutines-android]
  patterns: [sealed-interface-result-type, state-machine-with-retry, qualifier-based-dispatcher-injection, flow-throttling]

key-files:
  created:
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/di/Qualifiers.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/di/DispatcherModule.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/result/AppResult.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/result/AppError.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/result/PermissionKind.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/statemachine/ConnectionStateMachine.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/statemachine/ConnectionMachineState.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/statemachine/ConnectionEvent.kt
    - android/sdk/common/src/main/kotlin/app/dqxn/android/sdk/common/flow/FlowExtensions.kt
    - android/sdk/common/src/test/kotlin/app/dqxn/android/sdk/common/result/AppResultTest.kt
    - android/sdk/common/src/test/kotlin/app/dqxn/android/sdk/common/statemachine/ConnectionStateMachineTest.kt
  modified:
    - android/sdk/common/build.gradle.kts

key-decisions:
  - "retryCount exposed as public read-only property for testability (private set)"
  - "Disconnect from any non-Idle state resets retryCount to 0 (clean disconnect = clean retry state)"
  - "SearchTimeout produces AppError.Device (not Network) since it's a device-discovery timeout"

patterns-established:
  - "Sealed interface with data object/class variants for state machines and error types"
  - "Qualifier annotations + Hilt module for coroutine dispatchers (no DQXNDispatchers interface)"
  - "ConnectionStateMachine with internal retry state, exponential backoff, max retries to Idle"
  - "jqwik property tests alongside JUnit5 unit tests for state machine invariants"

requirements-completed: [F3.6]

# Metrics
duration: 10min
completed: 2026-02-24
---

# Phase 2 Plan 01: SDK Common Foundation Summary

**Dispatcher DI, AppResult<T> with 7 extension functions, 7-variant AppError, ConnectionStateMachine with retry/backoff, 75 tests (JUnit5 + jqwik)**

## Performance

- **Duration:** 10 min
- **Started:** 2026-02-23T18:38:06Z
- **Completed:** 2026-02-23T18:48:00Z
- **Tasks:** 2
- **Files modified:** 12 (1 modified, 11 created)

## Accomplishments
- Full `:sdk:common` type surface: dispatchers, result types, error hierarchy, connection state machine, flow utilities
- 75 passing tests: AppResult (15), ConnectionStateMachine ported (8) + exhaustive matrix (42) + behavior (5) + jqwik properties (5)
- Both JUnit5 Jupiter and jqwik engines confirmed executing via XML output
- No OBU-specific error types (Obu, SdkAuth, ObuDataAccess) — clean generalized error hierarchy
- `:sdk:common` compiles as Android library with Hilt, ready for `:sdk:contracts` to depend on via `api(project(":sdk:common"))`

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement sdk:common types** - `7908eae` (feat — note: bundled with research commit due to concurrent staging)
2. **Task 2: Write sdk:common tests** - `5142305` (test)

**Plan metadata:** (pending)

## Files Created/Modified
- `android/sdk/common/build.gradle.kts` - Added coroutines dependencies
- `android/sdk/common/src/main/kotlin/.../di/Qualifiers.kt` - @IoDispatcher, @DefaultDispatcher, @MainDispatcher, @ApplicationScope
- `android/sdk/common/src/main/kotlin/.../di/DispatcherModule.kt` - Hilt module providing dispatchers + ApplicationScope
- `android/sdk/common/src/main/kotlin/.../result/AppResult.kt` - Sealed result type with Ok/Err + 7 extensions
- `android/sdk/common/src/main/kotlin/.../result/AppError.kt` - 7-variant error hierarchy
- `android/sdk/common/src/main/kotlin/.../result/PermissionKind.kt` - 6 permission types enum
- `android/sdk/common/src/main/kotlin/.../statemachine/ConnectionStateMachine.kt` - State machine with retry logic
- `android/sdk/common/src/main/kotlin/.../statemachine/ConnectionMachineState.kt` - 6 sealed states
- `android/sdk/common/src/main/kotlin/.../statemachine/ConnectionEvent.kt` - 7 sealed events
- `android/sdk/common/src/main/kotlin/.../flow/FlowExtensions.kt` - throttleLatest + catchAndLog
- `android/sdk/common/src/test/.../result/AppResultTest.kt` - 15 unit tests
- `android/sdk/common/src/test/.../statemachine/ConnectionStateMachineTest.kt` - 60 tests (ported + matrix + behavior + jqwik)

## Decisions Made
- **retryCount as public read-only:** Exposed `retryCount` with `private set` for test observability. The plan specified it as internal, but testing retry behavior requires inspection.
- **Disconnect resets retryCount from all states:** Ensures a clean disconnect always gives a fresh retry budget on reconnection.
- **SearchTimeout produces AppError.Device:** Not Network, since it's a device-discovery timeout, not a network failure.
- **No DQXNDispatchers interface:** As specified in phase-02.md, only qualifier annotations. The interface was dropped from the old codebase pattern.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed type inference for throw expressions in test lambdas**
- **Found during:** Task 2 (AppResultTest)
- **Issue:** `err.map { throw AssertionError(...) }` and `err.flatMap { throw ... }` couldn't infer return type R because `throw` is `Nothing`
- **Fix:** Added explicit `AppResult<Int>` type annotation to result variable
- **Files modified:** AppResultTest.kt
- **Verification:** Compilation passes
- **Committed in:** 5142305 (Task 2 commit)

**2. [Rule 1 - Bug] Removed data class from inner class scope**
- **Found during:** Task 2 (ConnectionStateMachineTest)
- **Issue:** `data class TransitionCase` inside `inner class ExhaustiveTransitionMatrix` is prohibited in Kotlin (inner classes cannot contain data class declarations)
- **Fix:** Removed unused TransitionCase data class, using `Arguments.of()` directly
- **Files modified:** ConnectionStateMachineTest.kt
- **Verification:** Compilation passes
- **Committed in:** 5142305 (Task 2 commit)

**3. [Rule 1 - Bug] Fixed isAnyOf assertion for parameterized state types**
- **Found during:** Task 2 (ConnectionStateMachineTest jqwik property)
- **Issue:** `isAnyOf` with object singletons fails when state is a parameterized data class (DeviceDiscovered, Error)
- **Fix:** Replaced with KClass-based type check using `validStateTypes.contains(lastState::class)`
- **Files modified:** ConnectionStateMachineTest.kt
- **Verification:** All 75 tests pass
- **Committed in:** 5142305 (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (3 bugs — all in test code)
**Impact on plan:** All auto-fixes were compilation/assertion bugs in test code. No scope creep. Production code had zero issues.

## Issues Encountered
- **Task 1 commit absorbed into research commit:** The `git add` for Task 1 files was staged before a concurrent research commit (`7908eae`), which absorbed the sdk:common source files. The code content is correct but the commit message is wrong ("docs(03): research phase domain"). Task 2 commit (`5142305`) is cleanly isolated.
- **JDK 25 not on default PATH:** Build required explicit `JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home`. JDK 21 is the system default.
- **Pre-existing spotless violations:** `data/proto/build.gradle.kts` had formatting issues from Phase 1. Spotless applied fixes during formatting check but the file was left unstaged (out of scope).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `:sdk:common` is ready for `:sdk:contracts` to depend on via `api(project(":sdk:common"))`
- All types (AppResult, AppError, ConnectionStateMachine, dispatchers) are available for contract definitions
- 75 tests provide regression safety for the foundation types
- Phase 2 Plan 02 (`:sdk:contracts` widget/provider types) can proceed immediately

## Self-Check: PASSED

- All 11 source/test files: FOUND
- Commit 7908eae (Task 1 code): FOUND
- Commit 5142305 (Task 2 tests): FOUND
- `:sdk:common:assembleDebug`: BUILD SUCCESSFUL
- `:sdk:common:testDebugUnitTest`: 75 tests passed, 0 failed

---
*Phase: 02-sdk-contracts-common*
*Completed: 2026-02-24*
