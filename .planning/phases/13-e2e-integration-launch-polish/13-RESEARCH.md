# Phase 13: E2E Integration + Launch Polish - Research

**Researched:** 2026-02-25
**Domain:** E2E integration testing, accessibility audit, privacy features, app lifecycle APIs, localization validation
**Confidence:** HIGH

## Summary

Phase 13 is the convergence point where all prior phases (9 chaos, 11 UI, 12 CI/benchmarks) are validated working together. It spans six distinct domains: (1) full E2E integration testing via the existing `AgenticTestClient` protocol against the agentic `ContentProvider`, (2) deterministic chaos CI gate, (3) WCAG AA accessibility audit across all 24 themes, (4) privacy features (Export My Data, PDPA/GDPR verification, Firebase analytics reset), (5) app lifecycle APIs (Google Play In-App Updates + In-App Review), and (6) localization validation (hardcoded text lint, locale-aware formatting).

The codebase is well-positioned. `AgenticTestClient` is fully specified in `.planning/arch/testing.md` with semantics helpers (`assertWidgetRendered`, `querySemanticsOne`, `awaitCondition`). `ChaosEngine` and `ChaosProviderInterceptor` exist in `:core:agentic` with deterministic seed-based scheduling. All 24 themes (2 free + 22 premium) exist as JSON files with hex color values that can be parsed programmatically for contrast ratio calculation. The `MainSettingsViewModel.deleteAllData()` already clears all 6 DataStore instances. The `AnalyticsTracker` interface needs a `resetAnalyticsData()` method added for NF-P4 Firebase ID reset.

**Primary recommendation:** Split into 7 plans: (1) Google Play libraries + version catalog + AppUpdateManager/ReviewManager, (2) Export My Data feature + PDPA/GDPR verification tests, (3) WCAG AA contrast audit + TalkBack + font scale + reduced motion + color-blind safety tests, (4) AgenticTestClient infrastructure + basic E2E journey test, (5) Chaos CI gate + multi-pack Hilt binding validation, (6) Localization lint + locale-aware formatting verification, (7) Battery/memory soak test infrastructure + NF-D2 disclaimer verification. Keep plans scoped so executors don't overload context windows -- each is an independent domain.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.android.play:app-update-ktx` | 2.1.0 | In-app update flow (NF-L2) | Official Google Play API, KTX coroutine extensions |
| `com.google.android.play:review-ktx` | 2.0.1 | In-app review prompt (NF-L3) | Official Google Play API, `FakeReviewManager` for testing |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 | E2E instrumented tests (already in version catalog) | Required for `AgenticTestClient` shell command execution |
| `kotlinx-serialization-json` | 1.10.0 | JSON export for Export My Data (already in project) | Consistent with project serialization stack |
| `androidx.compose.ui:ui-test-junit4` | BOM-managed | Compose semantics testing (already in version catalog) | Standard Compose test library |
| Truth | 1.4.4 | Assertions (already in project) | Project standard |
| MockK | 1.14.9 | Mocking (already in project) | Project standard |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `FakeAppUpdateManager` | Bundled with app-update 2.1.0 | Test in-app update flows | Unit tests for update logic |
| `FakeReviewManager` | Bundled with review 2.0.1 | Test in-app review flows | Unit tests for review trigger logic |
| `ActivityResultContracts.CreateDocument` | AndroidX Activity (already in project) | File picker for Export My Data | JSON export file creation |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `AgenticTestClient` (agentic protocol) | UiAutomator raw commands | Agentic protocol is already specified, tests reuse agent command paths -- no parallel infra |
| Programmatic contrast ratio calc | Accessibility Scanner app | Scanner is manual; programmatic testing meets zero-manual-tests policy |
| kotlinx-serialization JSON export | Protobuf binary export | JSON is human-readable (GDPR Article 15 requirement), no additional dependency |

## Architecture Patterns

### Recommended Project Structure

```
app/src/androidTest/kotlin/app/dqxn/android/       # E2E instrumented tests
  e2e/
    AgenticTestClient.kt                             # Agentic protocol wrapper
    SemanticsNodeResult.kt                           # Semantics query result type
    FullJourneyE2ETest.kt                            # SC1: launch-to-settings flow
    ChaosCorrelationE2ETest.kt                       # SC2: deterministic chaos gate
    MultiPackE2ETest.kt                              # SC3: multi-pack Hilt validation
app/src/main/kotlin/app/dqxn/android/
  lifecycle/
    AppUpdateCoordinator.kt                          # NF-L2: In-app updates
    AppReviewCoordinator.kt                          # NF-L3: In-app review trigger
feature/settings/src/main/kotlin/.../
  privacy/
    DataExporter.kt                                  # NF-P5: Export My Data
    DataExportRoute (in OverlayNavHost or Settings)   # Settings > Data & Privacy
feature/settings/src/test/kotlin/.../
  privacy/
    DataExporterTest.kt                              # Export round-trip test
app/src/test/kotlin/.../
  lifecycle/
    AppUpdateCoordinatorTest.kt                      # FakeAppUpdateManager tests
    AppReviewCoordinatorTest.kt                      # FakeReviewManager tests
  accessibility/
    ThemeContrastAuditTest.kt                        # WCAG AA contrast for all 24 themes
  localization/
    HardcodedTextLintTest.kt                         # NF-I1 lint verification
```

### Pattern 1: AgenticTestClient Protocol (E2E)

**What:** Instrumented tests use the same agentic `ContentProvider` protocol that the debug agent uses. `AgenticTestClient` wraps `content call` commands with assertion helpers.
**When to use:** All E2E tests that need to verify on-device state, semantics trees, or trigger mutations.
**Confidence:** HIGH -- fully specified in `.planning/arch/testing.md` with code examples.

```kotlin
// From .planning/arch/testing.md
class AgenticTestClient(private val device: UiDevice) {
    fun send(command: String, params: Map<String, Any> = emptyMap()): JsonObject {
        val paramsJson = Json.encodeToString(params)
        val output = device.executeShellCommand(
            "content call --uri content://app.dqxn.android.agentic " +
            "--method $command --arg '$paramsJson'"
        )
        val filePath = output.substringAfter("filePath=").substringBefore("}")
        val json = device.executeShellCommand("cat $filePath")
        val response = Json.parseToJsonElement(json).jsonObject
        val status = response["status"]?.jsonPrimitive?.content
        check(status == "ok") { "Command '$command' failed: ${response["message"]}" }
        return response
    }

    fun assertWidgetRendered(widgetId: String) { /* semantics query */ }
    fun assertWidgetNotRendered(widgetId: String) { /* semantics query */ }
    fun awaitCondition(command: String, path: String, expected: Any, ...) { /* poll */ }
}
```

### Pattern 2: Programmatic WCAG AA Contrast Audit

**What:** Pure-math unit test that extracts colors from all 24 themes (2 built-in `DashboardThemeDefinition` objects + 22 JSON files), computes relative luminance, and checks contrast ratios against WCAG AA thresholds.
**When to use:** Test all themes programmatically without requiring a device.
**Confidence:** HIGH -- WCAG formula is well-defined, theme colors are fully accessible from test code.

```kotlin
// Relative luminance per WCAG 2.1
fun Color.relativeLuminance(): Double {
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun linearize(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun contrastRatio(l1: Double, l2: Double): Double {
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

// Test: for each theme, check primaryTextColor vs background average color
// Normal text: >= 4.5:1, Large text (>=18sp bold or >=24sp): >= 3:1
```

### Pattern 3: Google Play API Testing with Fakes

**What:** `FakeAppUpdateManager` and `FakeReviewManager` simulate the Play Store update/review flows without requiring a Play Store connection. Inject via Hilt qualifier or constructor parameter.
**When to use:** Unit tests for update/review trigger logic.

```kotlin
@Test
fun `immediate update triggered when critical version available`() {
    val fakeManager = FakeAppUpdateManager(context)
    fakeManager.setUpdateAvailable(2) // version code 2 > current 1
    val coordinator = AppUpdateCoordinator(fakeManager)
    coordinator.checkForUpdate(activity)
    assertThat(fakeManager.isImmediateFlowVisible).isTrue()
}

@Test
fun `review not triggered before 3 sessions`() {
    val fakeManager = FakeReviewManager(context)
    val coordinator = AppReviewCoordinator(fakeManager, prefs)
    coordinator.maybeRequestReview(activity)
    // FakeReviewManager.launchReviewFlow would succeed but coordinator gating prevents call
    verify(exactly = 0) { fakeManager.requestReviewFlow() }
}
```

### Pattern 4: Export My Data (NF-P5)

**What:** Inject all 5 DataStore repositories + `AnalyticsTracker`, read current values, serialize to JSON via `kotlinx.serialization`, write to user-selected file via `ActivityResultContracts.CreateDocument`.
**When to use:** Settings > Data & Privacy > Export My Data.

```kotlin
class DataExporter @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val providerSettingsStore: ProviderSettingsStore,
    private val pairedDeviceStore: PairedDeviceStore,
    private val widgetStyleStore: WidgetStyleStore,
) {
    suspend fun exportToJson(): String {
        // Collect current values from each store
        // Serialize to JSON structure
        // Return JSON string
    }
}
```

### Anti-Patterns to Avoid

- **Screenshot-based accessibility testing:** Violates zero-manual-tests policy. Use programmatic contrast ratio calculation instead.
- **Real Play Store calls in tests:** `FakeAppUpdateManager`/`FakeReviewManager` exist precisely for this. Never call `AppUpdateManagerFactory.create()` in tests.
- **`Thread.sleep` without polling in E2E:** Use `awaitCondition` with poll + timeout pattern from `AgenticTestClient`. Bare `Thread.sleep` is flaky.
- **Robolectric for E2E tests:** E2E tests run as instrumented tests on device/emulator. Robolectric is for unit-level compose testing only.
- **Testing contrast on rendered pixels:** Parse color values from theme definitions directly. Rendered pixel sampling is fragile and slow.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| In-app update flow | Custom APK version check + download | `com.google.android.play:app-update-ktx` 2.1.0 | Handles download, install, restart, Play Store integration |
| In-app review prompt | Custom star-rating dialog | `com.google.android.play:review-ktx` 2.0.1 | Play Store policy requires official API for review prompts |
| Update flow testing | Manual APK sideloading | `FakeAppUpdateManager` | Deterministic, fast, no device needed |
| Review flow testing | Play Console internal testing | `FakeReviewManager` | Unit-testable, no Play Store dependency |
| WCAG contrast calculation | Manual eyeballing or screenshot comparison | Relative luminance formula from WCAG 2.1 spec | Deterministic, reproducible, automated |
| JSON serialization | Hand-built JSON strings | `kotlinx.serialization.json` (already in project) | Type-safe, handles edge cases |

**Key insight:** The two Google Play APIs (`app-update` and `review`) both ship with test doubles (`FakeAppUpdateManager`, `FakeReviewManager`) that make unit testing trivial. The E2E testing infrastructure (`AgenticTestClient`) is fully specified in the architecture docs and reuses the existing agentic protocol.

## Common Pitfalls

### Pitfall 1: FakeAppUpdateManager Method Call Ordering

**What goes wrong:** `FakeAppUpdateManager` requires methods to be called in a specific order (e.g., `setUpdateAvailable()` before `checkUpdateAvailability()`, then `startUpdateFlowForResult()`, then `userAcceptsUpdate()`). Calling out of order throws `IllegalStateException`.
**Why it happens:** The fake simulates the real flow's state machine. Unlike a mock, it enforces flow ordering.
**How to avoid:** Follow the documented sequence: set availability -> request info -> start flow -> user action -> download events -> complete install. Write a wrapper that enforces this sequence.
**Warning signs:** `IllegalStateException` from `FakeAppUpdateManager` in tests.

### Pitfall 2: FakeAppUpdateManager Does Not Trigger onActivityResult

**What goes wrong:** `userAcceptsUpdate()` and `userRejectsUpdate()` do NOT trigger `onActivityResult`. Tests relying on activity result callbacks will hang.
**Why it happens:** `FakeAppUpdateManager` is self-contained -- it doesn't interact with the Android activity lifecycle.
**How to avoid:** Structure update coordinator to check `FakeAppUpdateManager.isImmediateFlowVisible` / `isFlexibleUpdateVisible` in tests rather than waiting for activity result callbacks. Or manually invoke the result callback in tests.
**Warning signs:** Test hangs waiting for activity result after `userAcceptsUpdate()`.

### Pitfall 3: Contrast Ratio Requires Solid Background Color

**What goes wrong:** WCAG contrast ratio formula requires two solid colors. Gradient backgrounds (which most themes use) have no single "background color" -- the contrast varies across the gradient.
**Why it happens:** All 24 themes use gradient backgrounds (`backgroundGradient` with multiple stops).
**How to avoid:** For gradient backgrounds, check contrast against EVERY gradient stop color. The minimum contrast across all stops must meet the threshold. For `widgetBackgroundBrush`, check alpha-composited widget background over each gradient stop of the parent background.
**Warning signs:** Tests pass with first gradient stop but fail at darker/lighter stops.

### Pitfall 4: Export My Data Proto DataStore Serialization

**What goes wrong:** Proto DataStore stores binary protobuf, not JSON. You can't just "read the file." You need to collect current values via the repository interfaces and serialize them to JSON.
**Why it happens:** The DataStore layer abstracts protobuf storage behind `Flow<T>` interfaces.
**How to avoid:** Inject repository interfaces (`LayoutRepository`, `UserPreferencesRepository`, etc.), collect `.first()` from their flows, map to serializable data classes, then use `kotlinx.serialization.json` to produce JSON.
**Warning signs:** Attempting to read `.proto` files directly or serialize Proto-generated classes to JSON.

### Pitfall 5: AnalyticsTracker Missing resetAnalyticsData()

**What goes wrong:** NF-P4 requires Firebase Analytics ID reset via `FirebaseAnalytics.resetAnalyticsData()`. The current `AnalyticsTracker` interface only has `setEnabled()/track()/setUserProperty()` -- no reset method.
**Why it happens:** The interface was defined in Phase 2 before NF-P4 was scoped.
**How to avoid:** Add `resetAnalyticsData()` to the `AnalyticsTracker` interface. Implement in `FirebaseAnalyticsTracker` by calling `firebaseAnalytics.resetAnalyticsData()`. Add no-op to `NoOpAnalyticsTracker`. Call from `MainSettingsViewModel.deleteAllData()`.
**Warning signs:** `deleteAllData()` clears stores but Firebase analytics ID persists.

### Pitfall 6: E2E Tests Require Non-Release Build + Agentic ContentProvider

**What goes wrong:** `AgenticTestClient` sends commands to `content://app.dqxn.android.agentic`, which exists in debug and benchmark builds but not release. Running E2E tests against release builds fails with "provider not found."
**Why it happens:** `AgenticContentProvider` lives in `src/agentic/` shared source set, compiled into debug and benchmark variants only. Release has no agentic infrastructure.
**How to avoid:** E2E instrumented tests MUST use debug or benchmark build variant. Debug is the default `androidTest` target.
**Warning signs:** "Unknown URI content://app.dqxn.android.agentic" errors in instrumented tests.

### Pitfall 7: In-App Review Frequency Cap State

**What goes wrong:** The 90-day frequency cap for review prompts needs persistent state (last review timestamp). If stored only in memory, app restart loses the cap, and users get prompted again.
**Why it happens:** Easy to forget that `UserPreferencesRepository` needs a new field for last review prompt timestamp.
**How to avoid:** Add `lastReviewPromptTimestamp: Flow<Long>` and `setLastReviewPromptTimestamp(timestamp: Long)` to `UserPreferencesRepository`. Check `System.currentTimeMillis() - lastTimestamp > 90 * 24 * 3600 * 1000L` before prompting.
**Warning signs:** Review prompt shows every session instead of once per 90 days.

## Code Examples

### WCAG AA Contrast Ratio Calculation

```kotlin
// Source: WCAG 2.1 spec - Understanding SC 1.4.3
// https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

object WcagContrastChecker {

    /** WCAG 2.1 relative luminance. */
    fun Color.relativeLuminance(): Double {
        val r = linearize(red)
        val g = linearize(green)
        val b = linearize(blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** Contrast ratio between two colors per WCAG 2.1. Range: 1.0 to 21.0. */
    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.relativeLuminance()
        val l2 = background.relativeLuminance()
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Checks WCAG AA: 4.5:1 normal text, 3:1 large text. */
    fun meetsAA(foreground: Color, background: Color, isLargeText: Boolean = false): Boolean {
        val ratio = contrastRatio(foreground, background)
        return if (isLargeText) ratio >= 3.0 else ratio >= 4.5
    }
}
```

### Export My Data JSON Structure

```kotlin
// NF-P5: Human-readable JSON export
@Serializable
data class DataExport(
    val exportTimestamp: Long,
    val appVersion: String,
    val layout: LayoutExport,
    val preferences: PreferencesExport,
    val providerSettings: Map<String, Map<String, String>>,
    val pairedDevices: List<PairedDeviceExport>,
    val widgetStyles: Map<String, Map<String, String>>,
)

@Serializable
data class LayoutExport(
    val profiles: List<ProfileExport>,
    val activeProfileId: String,
)

@Serializable
data class ProfileExport(
    val profileId: String,
    val displayName: String,
    val widgets: List<WidgetExport>,
)

@Serializable
data class WidgetExport(
    val instanceId: String,
    val typeId: String,
    val position: GridPositionExport,
    val size: GridSizeExport,
)
```

### AppUpdateCoordinator Pattern

```kotlin
// NF-L2: In-app update coordinator
class AppUpdateCoordinator(
    private val appUpdateManager: AppUpdateManager,
    private val prefs: UserPreferencesRepository,
) {
    fun checkForUpdate(activity: Activity) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                val updateType = if (appUpdateInfo.updatePriority() >= 4) {
                    AppUpdateType.IMMEDIATE  // Critical bug fix
                } else {
                    AppUpdateType.FLEXIBLE   // Feature update
                }
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo, activityResultLauncher, AppUpdateOptions.newBuilder(updateType).build()
                )
            }
        }
    }
}
```

### AppReviewCoordinator Pattern

```kotlin
// NF-L3: In-app review with trigger conditions
class AppReviewCoordinator(
    private val reviewManager: ReviewManager,
    private val prefs: UserPreferencesRepository,
) {
    suspend fun maybeRequestReview(activity: Activity) {
        val sessionCount = prefs.sessionCount.first()
        val hasCustomized = prefs.hasCustomizedLayout.first()
        val hasCrashed = prefs.hasCrashedThisSession.first()
        val lastReview = prefs.lastReviewPromptTimestamp.first()
        val now = System.currentTimeMillis()
        val ninetyDaysMs = 90L * 24 * 3600 * 1000

        if (sessionCount >= 3 && hasCustomized && !hasCrashed &&
            (lastReview == 0L || now - lastReview > ninetyDaysMs)) {
            val reviewInfo = reviewManager.requestReviewFlow().await()
            reviewManager.launchReviewFlow(activity, reviewInfo).await()
            prefs.setLastReviewPromptTimestamp(now)
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.google.android.play:core` monolith | Split into `app-update`, `review`, `asset-delivery` | Play Core 2.0 (2022) | Must use split libraries, not monolith |
| `startUpdateFlowForResult(requestCode)` | `ActivityResultLauncher<IntentSenderRequest>` | Activity 1.2.0+ | No more `onActivityResult` override |
| Manual contrast checking | WCAG 2.1 spec + programmatic testing | WCAG 2.1 (2018) | 3:1 for UI components, not just text |
| GDPR-only compliance | PDPA (Singapore) + GDPR dual compliance | PDPA fully effective 2025 | Both must be addressed in consent flow |

**Deprecated/outdated:**
- `com.google.android.play:core` (monolith) -- replaced by split libraries since Play Core 2.0
- `startUpdateFlowForResult(activity, requestCode)` -- replaced by `ActivityResultLauncher`
- `onActivityResult()` for update results -- use `registerForActivityResult()`

## Open Questions

1. **Battery drain measurement automation**
   - What we know: NF11 requires < 5% per hour screen-on, NF37 requires near-zero background drain. Phase 12 delivers benchmark infrastructure.
   - What's unclear: Whether battery drain can be reliably measured in CI without physical device thermal variance. `dumpsys batterystats` gives estimates but physical measurement is more reliable.
   - Recommendation: Use `dumpsys batterystats --reset` + 30-minute soak + `dumpsys batterystats` delta as automated approximation. Document that the 5%/hour threshold should be validated on reference hardware (Pixel 7a) before launch. This is an automated instrumented test, not a manual test -- script it and assert.

2. **UserPreferencesRepository fields for review/update tracking**
   - What we know: NF-L3 needs `sessionCount`, `hasCustomizedLayout`, `lastReviewPromptTimestamp`. `hasCrashedThisSession` needs to be tracked per-session.
   - What's unclear: Some of these may already exist or can be derived (session count from `SessionRecorder` in Phase 11). Need to check what's available vs. what needs adding.
   - Recommendation: Audit existing `UserPreferencesRepository` fields. Add only what's missing: `lastReviewPromptTimestamp` (Long), `sessionCount` (Int). Derive `hasCustomizedLayout` from `LayoutRepository` (non-empty widget list). `hasCrashedThisSession` from `CrashEvidenceWriter` in-session state.

3. **NF-D2 Terms of Service disclaimer scope**
   - What we know: "Terms of service must explicitly disclaim liability for speed data accuracy."
   - What's unclear: Whether this requires an in-app ToS screen or just Play Store listing text. Phase 11 already delivered NF-D1 (widget info page disclaimer) and NF-D3 (first-launch onboarding disclaimer).
   - Recommendation: NF-D2 is a Play Store listing requirement + legal document, not necessarily a code deliverable. Verify wording exists in a ToS string resource that can be shown in-app via Settings > About > Terms of Service. Minimal code.

4. **Multi-pack Hilt binding conflict detection**
   - What we know: SC3 requires essentials + themes + demo loaded simultaneously without conflicts. All three packs are already in `:app` deps.
   - What's unclear: Whether the existing `:app` build already validates this (it should -- compilation succeeds with all three).
   - Recommendation: Compilation passing is necessary but not sufficient. Add an explicit Hilt integration test that verifies `Set<WidgetRenderer>`, `Set<DataProvider<*>>`, `Set<ThemeProvider>`, `Set<DashboardPackManifest>` contain expected counts. This is a JUnit4 `@HiltAndroidTest`.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| NF11 | Battery drain < 5%/hour screen-on, 12 widgets, BLE connected | Battery soak test via `dumpsys batterystats` delta measurement. Automated instrumented test. |
| NF24 | App fully functional offline | E2E journey test with airplane mode -- all sensor data local, all persistence local. Verify via `AgenticTestClient`. |
| NF25 | Hardware device data via Bluetooth -- no internet dependency | Covered by NF24 offline verification. BLE providers don't use internet. |
| NF26 | Internet required only for entitlement purchase/restore and weather | E2E test: disable network, verify dashboard renders with all local providers. Weather widget shows offline fallback. |
| NF30 | WCAG 2.1 AA contrast ratios for critical text | Programmatic contrast audit: extract all 24 theme colors, compute relative luminance, assert 4.5:1 for normal text, 3:1 for large text. Pure unit test. |
| NF32 | TalkBack support for setup/settings flows | Compose semantics traversal test: verify `contentDescription` on all interactive elements in settings/setup. Focus order validation. JUnit4 compose test with `ComposeTestRule`. |
| NF33 | System font scale respected in settings UI | Compose test: set `fontScale` in `LocalDensity`, verify no layout overflow/clipping at 1.0x through 2.0x. |
| NF37 | Background battery < 1%/hr with BLE, < 0.1% with none | Background soak test: app backgrounded, `dumpsys batterystats` delta. Verify `callbackFlow` `awaitClose` unregisters all sensors. |
| NF-D2 | ToS must disclaim speed data accuracy liability | String resource verification: ToS text contains speed accuracy disclaimer. Accessible from Settings. |
| NF-I1 | All user-facing strings in Android string resources | Android lint `HardcodedText` check + grep for string literals in `@Composable` functions. Pure CI script. |
| NF-L2 | In-app updates: IMMEDIATE for critical bugs, FLEXIBLE for features | `AppUpdateCoordinator` using `app-update-ktx` 2.1.0. Tests via `FakeAppUpdateManager`. |
| NF-L3 | In-app review: 3+ sessions, 1+ customization, no crash, 90-day cap | `AppReviewCoordinator` using `review-ktx` 2.0.1. Tests via `FakeReviewManager`. Trigger conditions gated by `UserPreferencesRepository` fields. |
| NF-P3 | PDPA compliance: consent before analytics, privacy policy URL, Delete All Data | End-to-end consent flow verification: opt-in -> events fire -> opt-out -> events stop. `deleteAllData()` already implemented. Verify privacy policy URL string resource exists. |
| NF-P4 | Data export + Firebase Analytics ID reset | Add `resetAnalyticsData()` to `AnalyticsTracker` interface. Call `firebaseAnalytics.resetAnalyticsData()` in `FirebaseAnalyticsTracker`. Invoke from `deleteAllData()`. |
| NF-P5 | GDPR Article 15: Export My Data in Settings | `DataExporter` class injecting all 5 repositories. Serialize to JSON. `ActivityResultContracts.CreateDocument` for file output. Round-trip test: export -> parse -> verify all keys present. |
</phase_requirements>

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 (unit) + JUnit4 (Hilt integration, compose, instrumented) |
| Config file | Build convention plugins (`dqxn.android.test`, `dqxn.android.hilt`) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.ThemeContrastAuditTest" --console=plain` |
| Full suite command | `./gradlew test --console=plain` |
| Instrumented command | `./gradlew :app:connectedDebugAndroidTest --console=plain` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NF11 | Battery < 5%/hr screen-on | instrumented soak | `./gradlew :app:connectedDebugAndroidTest --tests "*.BatterySoakTest"` | Wave 0 gap |
| NF24 | Fully functional offline | instrumented E2E | `./gradlew :app:connectedDebugAndroidTest --tests "*.OfflineE2ETest"` | Wave 0 gap |
| NF25 | BLE no internet | covered by NF24 | (same as NF24) | Wave 0 gap |
| NF26 | Internet only for entitlement/weather | instrumented E2E | `./gradlew :app:connectedDebugAndroidTest --tests "*.OfflineE2ETest"` | Wave 0 gap |
| NF30 | WCAG AA contrast | unit | `./gradlew :app:testDebugUnitTest --tests "*.ThemeContrastAuditTest"` | Wave 0 gap |
| NF32 | TalkBack setup/settings | compose UI test | `./gradlew :feature:settings:testDebugUnitTest --tests "*.TalkBackAccessibilityTest"` | Wave 0 gap |
| NF33 | Font scale settings UI | compose UI test | `./gradlew :feature:settings:testDebugUnitTest --tests "*.FontScaleTest"` | Wave 0 gap |
| NF37 | Background battery | instrumented soak | `./gradlew :app:connectedDebugAndroidTest --tests "*.BackgroundBatterySoakTest"` | Wave 0 gap |
| NF-D2 | ToS speed disclaimer | unit | `./gradlew :app:testDebugUnitTest --tests "*.ToSDisclaimerTest"` | Wave 0 gap |
| NF-I1 | No hardcoded text | lint | `./gradlew lintDebug --console=plain` (HardcodedText) | Existing lint rule |
| NF-L2 | In-app updates | unit | `./gradlew :app:testDebugUnitTest --tests "*.AppUpdateCoordinatorTest"` | Wave 0 gap |
| NF-L3 | In-app review | unit | `./gradlew :app:testDebugUnitTest --tests "*.AppReviewCoordinatorTest"` | Wave 0 gap |
| NF-P3 | PDPA consent flow | unit + integration | `./gradlew :feature:settings:testDebugUnitTest --tests "*.AnalyticsConsentFlowTest"` | Wave 0 gap |
| NF-P4 | Firebase analytics reset | unit | `./gradlew :core:firebase:testDebugUnitTest --tests "*.FirebaseAnalyticsTrackerTest"` | Partially exists |
| NF-P5 | Export My Data | unit | `./gradlew :feature:settings:testDebugUnitTest --tests "*.DataExporterTest"` | Wave 0 gap |

### Nyquist Sampling Rate

- **Minimum sample interval:** After every committed task -> run: `./gradlew test --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** Full suite green + `./gradlew :app:connectedDebugAndroidTest` before verification
- **Instrumented tests:** Run when device/emulator available, not blocking unit test feedback loop

### Wave 0 Gaps (must be created before implementation)

- [ ] `app/src/test/.../accessibility/ThemeContrastAuditTest.kt` -- covers NF30
- [ ] `app/src/test/.../lifecycle/AppUpdateCoordinatorTest.kt` -- covers NF-L2
- [ ] `app/src/test/.../lifecycle/AppReviewCoordinatorTest.kt` -- covers NF-L3
- [ ] `feature/settings/src/test/.../privacy/DataExporterTest.kt` -- covers NF-P5
- [ ] `app/src/androidTest/.../e2e/AgenticTestClient.kt` -- E2E infrastructure
- [ ] `app/src/androidTest/.../e2e/FullJourneyE2ETest.kt` -- covers SC1
- [ ] `app/src/androidTest/.../e2e/ChaosCorrelationE2ETest.kt` -- covers SC2
- [ ] `app/src/androidTest/.../e2e/MultiPackE2ETest.kt` -- covers SC3
- [ ] Version catalog entries for `app-update-ktx` and `review-ktx`

## Plan Decomposition Recommendation

To maintain minimal context pressure per executor, split Phase 13 into 7 focused plans:

### Plan 13-01: Google Play Libraries + App Lifecycle APIs (NF-L2, NF-L3)
- Version catalog: `play-app-update` 2.1.0, `play-review` 2.0.1
- `AppUpdateCoordinator` in `:app` with `FakeAppUpdateManager` tests
- `AppReviewCoordinator` in `:app` with `FakeReviewManager` tests
- `UserPreferencesRepository` extensions: `sessionCount`, `lastReviewPromptTimestamp`
- Wire into `MainActivity.onResume()`
- ~6 test files, self-contained domain

### Plan 13-02: Export My Data + Privacy Verification (NF-P3, NF-P4, NF-P5)
- `DataExporter` class in `:feature:settings`
- `AnalyticsTracker.resetAnalyticsData()` interface addition + implementations
- Wire `resetAnalyticsData()` into `MainSettingsViewModel.deleteAllData()`
- Export JSON structure with round-trip test
- PDPA consent end-to-end verification test
- Privacy policy URL string resource verification
- ~4 test files, privacy-focused domain

### Plan 13-03: WCAG AA Accessibility Audit (NF30, NF32, NF33)
- `WcagContrastChecker` utility (relative luminance + contrast ratio)
- Theme contrast audit: all 24 themes (2 built-in + 22 JSON) against critical text
- Gradient background handling (check all stops)
- TalkBack semantics traversal test for settings/setup
- Font scale verification at 1.0x-2.0x
- ~3 test files, accessibility domain

### Plan 13-04: AgenticTestClient + E2E Journey (SC1)
- `AgenticTestClient` implementation (from arch/testing.md spec)
- `SemanticsNodeResult` data class
- Full E2E journey: launch -> load -> bind -> render -> edit -> add/remove/resize -> theme switch -> settings
- Semantics verification at each step
- NF-D2 ToS disclaimer verification
- ~3 test files, E2E infrastructure + journey

### Plan 13-05: Chaos CI Gate + Multi-Pack Validation (SC2, SC3)
- `ChaosCorrelationE2ETest`: seed=42 deterministic fault injection -> `assertChaosCorrelation()`
- `MultiPackE2ETest`: Hilt integration test verifying Set<WidgetRenderer>/Set<DataProvider>/Set<ThemeProvider> counts
- Diagnostics artifact collection on failure
- Agentic debug loop validation: inject -> detect -> investigate -> verify
- ~3 test files, chaos + integration domain

### Plan 13-06: Localization + Offline Validation (NF-I1, NF24, NF25, NF26)
- Android lint `HardcodedText` gate (configure lint to error on HardcodedText)
- Grep for string literals in `@Composable` functions (CI script)
- Locale-aware formatting verification in essentials pack renderers
- Offline E2E test: airplane mode -> dashboard renders -> local providers work
- ~2-3 test files, localization + offline domain

### Plan 13-07: Battery/Memory Soak + Color-Blind Safety (NF11, NF37, NF40)
- Battery soak test infrastructure: `dumpsys batterystats` delta measurement
- 30-minute screen-on soak with 12 widgets
- Background battery soak: app backgrounded, verify sensor unregistration
- Memory leak check: heap dump after soak, no leaked Activity/ViewModel/Scope
- Color-blind safety: deuteranopia simulation for speed limit warnings (color + pattern + icon)
- ~3 test files, performance/accessibility soak domain

## Sources

### Primary (HIGH confidence)
- `.planning/arch/testing.md` -- AgenticTestClient protocol, E2E test patterns, test layer hierarchy
- `.planning/migration/phase-13.md` -- Phase 13 detailed specification
- `.planning/REQUIREMENTS.md` -- All NF requirement definitions
- Codebase: `ChaosEngine`, `ChaosProviderInterceptor`, `MainSettingsViewModel.deleteAllData()`, `DashboardThemeDefinition`, all 24 theme files

### Secondary (MEDIUM confidence)
- [In-App Updates Kotlin/Java Guide](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java) -- `app-update-ktx` 2.1.0, updated 2026-02-10
- [In-App Review API](https://developer.android.com/guide/playcore/in-app-review) -- `review-ktx` 2.0.1, updated 2026-01-30
- [FakeAppUpdateManager API](https://developer.android.com/reference/com/google/android/play/core/appupdate/testing/FakeAppUpdateManager) -- Test double documentation
- [FakeReviewManager API](https://developer.android.com/reference/com/google/android/play/core/review/testing/FakeReviewManager) -- Test double documentation
- [WCAG 2.1 Understanding SC 1.4.3](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html) -- Contrast ratio formula and thresholds

### Tertiary (LOW confidence)
- None -- all claims verified against official documentation or codebase

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- Google Play APIs are well-documented, versions verified against official docs (updated Feb 2026)
- Architecture: HIGH -- E2E protocol fully specified in architecture docs, theme colors accessible from test code, all DataStore repos already have `clearAll()`
- Pitfalls: HIGH -- FakeAppUpdateManager ordering documented in official API reference, gradient contrast edge case derived from inspecting actual theme JSON files, Proto DataStore serialization constraints verified against codebase
- Accessibility audit: HIGH -- WCAG 2.1 formula is a published standard, all theme color values are available in code/JSON

**Research date:** 2026-02-25
**Valid until:** 2026-03-25 (30 days -- stable APIs, well-defined requirements)
