---
phase: 06-deployable-app-agentic-framework
plan: 01
subsystem: agentic
tags: [agentic, command-routing, semantics, hilt-multibinding, ksp, compose-semantics]

# Dependency graph
requires:
  - phase: 04-ksp-codegen
    provides: "AgenticProcessor KSP processor that references @AgenticCommand and CommandHandler FQNs"
  - phase: 02-sdk-contracts-common
    provides: "SDK contracts and common utilities"
  - phase: 03-sdk-observability-analytics-ui
    provides: "Observability infrastructure"
provides:
  - "@AgenticCommand annotation (real, matching codegen stubs)"
  - "CommandHandler interface with execute(params, commandId) signature"
  - "AgenticCommandRouter dispatching commands by name via Hilt Set<CommandHandler>"
  - "CommandParams/CommandResult types with JSON serialization"
  - "SemanticsOwnerHolder for Compose semantics tree access"
  - "SemanticsSnapshot/SemanticsFilter for serializable tree capture and query"
affects: [06-02, 06-03, 06-04, 07-dashboard-shell, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "compileOnly compose-ui for semantics type references without Compose compiler"
    - "WeakReference for lifecycle-safe singleton holding of Compose objects"
    - "Any-typed register() to avoid hard runtime dependency on compose-ui"

key-files:
  created:
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/AgenticCommand.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/CommandHandler.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/AgenticCommandRouter.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/CommandParams.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/CommandResult.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/SemanticsOwnerHolder.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/SemanticsSnapshot.kt"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/SemanticsFilter.kt"
    - "android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/AgenticCommandRouterTest.kt"
    - "android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/SemanticsOwnerHolderTest.kt"
  modified:
    - "android/core/agentic/build.gradle.kts"

key-decisions:
  - "compose-ui as testImplementation for SemanticsOwner class loading in tests"
  - "CommandResult.toJson() parses data as raw JSON when valid, falls back to string primitive"
  - "AgenticCommandRouter indexes aliases alongside primary names in handlerMap"

patterns-established:
  - "compileOnly(compose.ui) + testImplementation(compose.ui) pattern for modules needing Compose types without Compose compiler"
  - "WeakReference holder pattern for lifecycle-scoped Compose objects in singleton scope"

requirements-completed: [F13.2, F13.11]

# Metrics
duration: 6min
completed: 2026-02-24
---

# Phase 6 Plan 1: Core Agentic Infrastructure Summary

**@AgenticCommand annotation, CommandHandler interface, AgenticCommandRouter with Hilt multibinding dispatch, and SemanticsOwnerHolder for Compose semantics tree access**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-24T03:48:46Z
- **Completed:** 2026-02-24T03:55:24Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- Real @AgenticCommand annotation matching the codegen:agentic stub shape exactly (name, description, category fields with SOURCE retention)
- CommandHandler interface with two-parameter execute(params, commandId) signature consistent with KSP stubs
- AgenticCommandRouter dispatching named commands via Hilt Set<CommandHandler> with alias support and exception wrapping
- SemanticsOwnerHolder with WeakReference for leak-safe Compose semantics tree capture and filtered query
- 14 unit tests (8 router, 6 holder) all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Core agentic types and AgenticCommandRouter** - `ad9fa99` (feat)
2. **Task 2: AgenticCommandRouter and SemanticsOwnerHolder unit tests** - `6666ca4` (test)

## Files Created/Modified
- `android/core/agentic/build.gradle.kts` - Added serialization plugin, sdk/common/observability deps, compose-ui compileOnly
- `android/core/agentic/src/main/.../AgenticCommand.kt` - @AgenticCommand annotation with SOURCE retention
- `android/core/agentic/src/main/.../CommandHandler.kt` - Handler interface with execute(params, commandId)
- `android/core/agentic/src/main/.../CommandParams.kt` - Command parameters data class with getString/requireString extensions
- `android/core/agentic/src/main/.../CommandResult.kt` - Sealed interface Success/Error with toJson() serialization
- `android/core/agentic/src/main/.../AgenticCommandRouter.kt` - Hilt-injected router dispatching by name/alias
- `android/core/agentic/src/main/.../SemanticsOwnerHolder.kt` - Singleton WeakReference holder for Compose SemanticsOwner
- `android/core/agentic/src/main/.../SemanticsSnapshot.kt` - Serializable semantics tree model
- `android/core/agentic/src/main/.../SemanticsFilter.kt` - Query filter with testTag/text/contentDescription/action matching
- `android/core/agentic/src/test/.../AgenticCommandRouterTest.kt` - 8 tests: dispatch, unknown, exceptions, trace, aliases
- `android/core/agentic/src/test/.../SemanticsOwnerHolderTest.kt` - 6 tests: null safety, lifecycle, type guard

## Decisions Made
- **compose-ui as testImplementation** -- compose-ui is `compileOnly` for main sources (no Compose compiler in `:core:agentic`), but `testImplementation` is needed because `SemanticsOwner` must be loadable at test runtime for the `is` type check in `register()`.
- **CommandResult.toJson() parses data as raw JSON** -- when `data` is valid JSON, it's embedded as a structured object in the response (not double-encoded as a string). Falls back to string primitive for non-JSON data.
- **Router indexes aliases** -- `AgenticCommandRouter` builds a lazy map that includes both primary names and aliases from `CommandHandler.aliases`, enabling alternative command names.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added testImplementation(compose-ui) for test classpath**
- **Found during:** Task 2 (unit tests)
- **Issue:** `SemanticsOwnerHolderTest` tests calling `register()` triggered `NoClassDefFoundError` because `SemanticsOwner` was only `compileOnly` -- not on the test runtime classpath.
- **Fix:** Added `testImplementation(platform(libs.compose.bom))` and `testImplementation(libs.compose.ui)` to core:agentic build.gradle.kts.
- **Files modified:** `android/core/agentic/build.gradle.kts`
- **Verification:** All 14 tests pass with 0 failures.
- **Committed in:** `6666ca4` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for test execution. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviation above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `:core:agentic` module fully compilable with all types the KSP processor references
- AgenticCommandRouter ready to receive handlers via Hilt multibinding (Plan 06-03 handlers)
- SemanticsOwnerHolder ready for registration by dashboard layer (Phase 7)
- CommandHandler interface compatible with KSP-generated `@Binds @IntoSet` module

## Self-Check: PASSED

All 12 files verified present. Both commits (ad9fa99, 6666ca4) verified in git log.

---
*Phase: 06-deployable-app-agentic-framework*
*Completed: 2026-02-24*
