---
phase: 02-sdk-contracts-common
plan: 05
subsystem: testing
tags: [junit4, junit5, jqwik, contract-tests, test-fixtures, property-testing, robolectric]

# Dependency graph
requires:
  - phase: 02-sdk-contracts-common
    plan: 03
    provides: WidgetRenderer, DataProvider, WidgetData, WidgetStyle, WidgetContext, SettingDefinition, SetupDefinition, Gated, ProviderFault, DataSnapshot, UnitSnapshot
provides:
  - WidgetRendererContractTest abstract base (14 assertions, JUnit4) for all pack widget renderers
  - WidgetRendererPropertyTest abstract base (jqwik property test, JUnit5) for settings survival
  - DataProviderContractTest abstract base (12 assertions, JUnit5) for all pack data providers
  - TestWidgetRenderer configurable stub with no-op Render()
  - TestDataProvider configurable fake with ProviderFault injection
  - TestWidgetScope CoroutineScope wrapper for widget testing
  - Factory functions (testWidgetData, testWidgetStyle, testWidgetContext)
  - testFixtures JAR consumable by downstream modules
affects: [08-essentials-pack, 09-themes-demo-chaos]

# Tech tracking
tech-stack:
  added: []
  patterns: [contract-test-inheritance, property-based-testing-with-jqwik, testfixtures-jar-sharing, three-engine-coexistence]

key-files:
  created:
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/WidgetRendererContractTest.kt
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/WidgetRendererPropertyTest.kt
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/DataProviderContractTest.kt
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/TestWidgetRenderer.kt
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/TestDataProvider.kt
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/TestWidgetScope.kt
    - android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/Factories.kt
    - android/sdk/contracts/src/test/kotlin/app/dqxn/android/sdk/contracts/testing/TestWidgetRendererContractTest.kt
    - android/sdk/contracts/src/test/kotlin/app/dqxn/android/sdk/contracts/testing/TestWidgetRendererPropertyTest.kt
    - android/sdk/contracts/src/test/kotlin/app/dqxn/android/sdk/contracts/testing/TestDataProviderContractTest.kt
  modified:
    - android/sdk/contracts/build.gradle.kts

key-decisions:
  - "Non-Compose fallback for contract tests #2 and #3: verify accessibilityDescription instead of Compose rendering, since :sdk:contracts has no Compose compiler"
  - "Cancellation test uses testScheduler.advanceTimeBy + runCurrent instead of advanceUntilIdle to avoid infinite-loop with continuous-emitting providers"

patterns-established:
  - "Contract test inheritance: abstract base in testFixtures, concrete subclass in src/test validates infrastructure, pack tests extend same base in Phase 8+"
  - "Three-engine coexistence: Vintage (JUnit4 WidgetRendererContractTest), Jupiter (JUnit5 DataProviderContractTest), jqwik (WidgetRendererPropertyTest) all run in single testDebugUnitTest task"
  - "TestDataProvider fault injection via MutableStateFlow<ProviderFault?> with transformLatest"

requirements-completed: [F2.1, F2.2, F2.19, F2.20, F3.1, F3.2]

# Metrics
duration: 9min
completed: 2026-02-24
---

# Phase 2 Plan 05: TestFixtures Contract Test Infrastructure Summary

**Abstract contract test bases (14-assertion WidgetRenderer, 12-assertion DataProvider, jqwik property test) with concrete validation tests, fault-injectable TestDataProvider, and testFixtures JAR packaging**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-23T19:12:35Z
- **Completed:** 2026-02-23T19:21:35Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- testFixtures with 7 files: WidgetRendererContractTest (14 assertions, JUnit4), WidgetRendererPropertyTest (jqwik, JUnit5), DataProviderContractTest (12 assertions, JUnit5), TestWidgetRenderer (configurable stub), TestDataProvider (ProviderFault injection), TestWidgetScope, and factory helpers
- Concrete validation tests: TestWidgetRendererContractTest (13 inherited tests pass), TestWidgetRendererPropertyTest (50-try jqwik property test passes), TestDataProviderContractTest (12 inherited tests pass) -- proves the abstract bases run, not just compile
- Three JUnit engines coexist in single test task: Vintage (JUnit4), Jupiter (JUnit5), jqwik
- testFixtures JAR produced (compile + runtime variants) and consumable by downstream modules
- Full regression: all 98 sdk:contracts tests pass (including plan 04 unit tests)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create testFixtures -- test stubs, factories, contract test bases** - `ad5757a` (feat)
2. **Task 2: Create concrete contract tests + verify testFixtures JAR consumption** - `ea41d45` (test)

**Plan metadata:** (pending)

## Files Created/Modified
- `.../testing/WidgetRendererContractTest.kt` - 14-assertion abstract JUnit4 test base for widget renderers
- `.../testing/WidgetRendererPropertyTest.kt` - jqwik property-based abstract JUnit5 test base (settings survival)
- `.../testing/DataProviderContractTest.kt` - 12-assertion abstract JUnit5 test base for data providers
- `.../testing/TestWidgetRenderer.kt` - Configurable WidgetRenderer stub with no-op Render()
- `.../testing/TestDataProvider.kt` - Configurable fake DataProvider with ProviderFault injection
- `.../testing/TestWidgetScope.kt` - TestScope wrapper for widget coroutine testing
- `.../testing/Factories.kt` - testWidgetData(), testWidgetStyle(), testWidgetContext() helpers
- `.../testing/TestWidgetRendererContractTest.kt` - Concrete contract test validating abstract base
- `.../testing/TestWidgetRendererPropertyTest.kt` - Concrete jqwik property test validating abstract base
- `.../testing/TestDataProviderContractTest.kt` - Concrete contract test validating abstract base
- `android/sdk/contracts/build.gradle.kts` - Added compose-ui to testFixtures deps

## Decisions Made
- **Non-Compose fallback for render tests:** `:sdk:contracts` has no Compose compiler, so contract tests #2 and #3 verify `accessibilityDescription()` instead of actual Compose rendering. Real Compose render tests will live in pack modules (Phase 8+) that have the Compose compiler. This avoids adding the Compose compiler to `:sdk:contracts` which would change the ABI of the interface (Compose compiler transforms `@Composable` function signatures).
- **testScheduler.advanceTimeBy for cancellation test:** `advanceUntilIdle()` causes infinite loop with continuous-emitting providers (each `delay(100)` resolves instantly in virtual time, creating unbounded iterations). Using `advanceTimeBy(200) + runCurrent()` advances just enough virtual time for one emission, then cancels cleanly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Float range assertion in WidgetRendererContractTest**
- **Found during:** Task 1
- **Issue:** Truth's `isIn()` doesn't accept Kotlin `ClosedFloatingPointRange<Float>` -- only Guava `Range<T>` or `Iterable<*>`
- **Fix:** Replaced `assertThat(default).isIn(min.rangeTo(max))` with `assertThat(default).isAtLeast(min)` + `assertThat(default).isAtMost(max)`
- **Files modified:** WidgetRendererContractTest.kt
- **Committed in:** ad5757a

**2. [Rule 1 - Bug] Fixed DataProviderContractTest cancellation test infinite loop**
- **Found during:** Task 2
- **Issue:** `advanceUntilIdle()` with an infinite-emitting flow causes `UncompletedCoroutinesError` because virtual time advances through unbounded delay/emit cycles
- **Fix:** Replaced `runTest(StandardTestDispatcher()) { advanceUntilIdle() }` with `runTest { testScheduler.advanceTimeBy(200); testScheduler.runCurrent() }` -- advances just enough for one emission cycle
- **Files modified:** DataProviderContractTest.kt
- **Committed in:** ea41d45

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes necessary for test correctness. No scope creep.

## Issues Encountered

- Plan 04 test files (ProviderFaultTest, SettingDefinitionTest, SetupDefinitionTest) were uncommitted in working tree from a prior execution. They got included in the Task 1 commit since git added untracked files in the test directory. Not harmful -- they belong to plan 04 and all pass.
- Pre-existing Spotless formatting issue in `data/proto/build.gradle.kts` and `SetupDefinitionTest.kt` from prior plans. Fixed via `spotlessApply` and included in Task 2 commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Contract test infrastructure complete and validated -- all pack widget/provider tests in Phase 8+ will extend these bases
- testFixtures JAR packaging confirmed working -- downstream modules can consume via `testImplementation(testFixtures(project(":sdk:contracts")))`
- Phase 2 is now complete (5/5 plans): all sdk:contracts types, common utilities, unit tests, and testFixtures delivered
- Phase 3 (sdk:observability, analytics, ui) and Phase 4 (KSP codegen) can proceed immediately

## Self-Check: PASSED
