---
phase: 08-essentials-pack
plan: "06b"
type: execute
wave: 2
depends_on: ["08-01"]
files_modified:
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/shortcuts/ShortcutsRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/solar/SolarRenderer.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/ShortcutsRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/SolarRendererTest.kt
autonomous: true
requirements:
  - F5.9
  - F5.11
  - NF-I2

must_haves:
  truths:
    - "ShortcutsRenderer displays app icon and handles tap action"
    - "SolarRenderer displays sunrise/sunset with optional 24h arc visualization"
  artifacts:
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/shortcuts/ShortcutsRenderer.kt"
      provides: "App launcher shortcut widget"
      contains: "@DashboardWidget"
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/solar/SolarRenderer.kt"
      provides: "Solar widget with dawn/day/dusk/night arc"
      contains: "@DashboardWidget"
  key_links:
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/shortcuts/ShortcutsRenderer.kt"
      to: "InfoCardLayout"
      via: "uses InfoCardLayout with app icon"
      pattern: "InfoCardLayout"
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/solar/SolarRenderer.kt"
      to: "SolarSnapshot"
      via: "reads sunrise/sunset for arc rendering"
      pattern: "SolarSnapshot"
---

<objective>
Implement Shortcuts and Solar widget renderers with contract tests.

Purpose: Shortcuts is an action-only widget (no data snapshot, uses CallActionProvider for tap-to-launch). Solar is the most complex Essentials widget — it has 3 display modes including a 24h circular arc with dawn/day/dusk/night color bands. Isolating Solar in its own plan prevents context pressure from simpler widgets.

Output: 2 widget renderers (ShortcutsRenderer, SolarRenderer) with contract tests + widget-specific rendering behavior tests.
</objective>

<execution_context>
@/Users/ohm/.claude/get-shit-done/workflows/execute-plan.md
@/Users/ohm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/08-essentials-pack/08-RESEARCH.md
@.planning/phases/08-essentials-pack/08-01-SUMMARY.md
@android/sdk/contracts/src/testFixtures/kotlin/app/dqxn/android/sdk/contracts/testing/WidgetRendererContractTest.kt
@android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetRenderer.kt
@android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardLayout.kt
@.planning/oldcodebase/packs.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement Shortcuts and Solar widget renderers</name>
  <files>
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/shortcuts/ShortcutsRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/solar/SolarRenderer.kt
  </files>
  <action>
    Both widgets follow the standard base pattern: `@DashboardWidget`, `@Inject constructor()`, `accessibilityDescription()`, settings schema, NF-I2 locale-aware formatting.

    **ShortcutsRenderer** — port from old:
    - typeId: `essentials:shortcuts`, displayName: "Shortcuts", defaultSize: 9x9
    - compatibleSnapshots: empty set (no data snapshot — action-only widget)
    - Settings: `packageName` (AppPickerSetting, default null, suggestedPackages: common nav/music apps), `displayName` (StringSetting, default ""), info card layout options
    - Render: `InfoCardLayout` with app icon loaded from `packageManager.getApplicationIcon(packageName)` via `remember`. Show app label when displayName is empty. Placeholder icon when no app selected.
    - Widget has `onTap` handling — but the actual launch is done by `CallActionProvider.execute()` which the binder routes to
    - accessibilityDescription: "Shortcut: Google Maps" or "Shortcut: tap to configure"

    **SolarRenderer** — port from old (most complex widget):
    - typeId: `essentials:solar`, displayName: "Solar", defaultSize: 10x8
    - compatibleSnapshots: `setOf(SolarSnapshot::class)`
    - Settings: `displayMode` (EnumSetting: NEXT_EVENT/SUNRISE_SUNSET/ARC, default NEXT_EVENT), `showArc` (Boolean, default true when ARC mode), `arcSize` (EnumSetting: SMALL/MEDIUM/LARGE, default MEDIUM), `timezoneId` (TimezoneSetting, default null)
    - Render modes:
      - NEXT_EVENT: shows countdown to next sunrise or sunset ("Sunset in 2h 15m")
      - SUNRISE_SUNSET: shows both times ("Sunrise 6:45 AM / Sunset 7:12 PM")
      - ARC: full 24h circular arc with dawn/day/dusk/night color bands + sun/moon marker at current position
    - Arc rendering: Canvas `drawArc` for each band. Sun position = `(currentTime - sunrise) / (sunset - sunrise)` mapped to arc angle. Dawn band: 30 minutes before sunrise. Dusk band: 30 minutes after sunset.
    - `drawWithCache` for the arc background (recalculates only when sunrise/sunset change, not every frame)
    - accessibilityDescription: "Solar: sunset at 7:12 PM" or "Solar: sunrise in 45 minutes"

    **Expose static computation functions on SolarRenderer for testing:**
    - `computeSunPosition(currentTimeMillis: Long, sunriseMillis: Long, sunsetMillis: Long): Float` — returns 0.0 at sunrise, 0.5 at solar noon, 1.0 at sunset
    - `computeArcAngle(sunPosition: Float, arcStartAngle: Float, arcSweepAngle: Float): Float`
    - `formatCountdown(deltaMillis: Long): String` — "2h 15m", "45m", etc.
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:compileDebugKotlin --console=plain 2>&1 | tail -5</automated>
  </verify>
  <done>Shortcuts and Solar widgets compile. Shortcuts handles app icon loading with InfoCardLayout. Solar has 3 display modes with 24h arc rendering via drawWithCache.</done>
</task>

<task type="auto">
  <name>Task 2: Contract tests and widget-specific tests for Shortcuts and Solar</name>
  <files>
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/ShortcutsRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/SolarRendererTest.kt
  </files>
  <action>
    **Both test classes extend `WidgetRendererContractTest` (JUnit4, 14 inherited assertions).** Each overrides `createRenderer()` and `createTestWidgetData()`.

    **ShortcutsRendererTest:**
    - `createTestWidgetData()`: `WidgetData.Empty` (no snapshot for shortcuts)
    - Widget-specific: accessibilityDescription with no package set says "tap to configure" or similar
    - compatibleSnapshots is empty set
    - Settings schema includes AppPickerSetting with key "packageName"

    **SolarRendererTest:**
    - `createTestWidgetData()`: `WidgetData.Empty.withSlot(SolarSnapshot::class, SolarSnapshot(sunriseEpochMillis = ..., sunsetEpochMillis = ..., solarNoonEpochMillis = ..., isDaytime = true, sourceMode = "timezone", timestamp = 1L))`
    - Widget-specific: `computeSunPosition(currentTime, sunrise, sunset)` returns 0.0 at sunrise, 0.5 at solar noon, 1.0 at sunset
    - Arc angle mapping: sun position 0.0 → arc start angle, 1.0 → arc end angle
    - `formatCountdown(7200000)` returns "2h 0m" or similar
    - `formatCountdown(2700000)` returns "45m" or similar
    - accessibilityDescription includes sunrise/sunset times
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:testDebugUnitTest --tests "*.ShortcutsRendererTest" --tests "*.SolarRendererTest" --console=plain 2>&1 | tail -15</automated>
  </verify>
  <done>Shortcuts and Solar contract tests pass 14 inherited assertions each. Solar arc position computation at sunrise/noon/sunset verified. Countdown formatting verified. Shortcuts handles no-package state.</done>
</task>

</tasks>

<verification>
1. `./gradlew :pack:essentials:testDebugUnitTest --tests "*.ShortcutsRendererTest" --tests "*.SolarRendererTest" --console=plain` — both widget tests pass
2. Solar arc computation: 0 at sunrise, 0.5 at noon, 1.0 at sunset
3. Shortcuts handles null package without crash
4. No `rememberCoroutineScope()` in any Render() function
</verification>

<success_criteria>
- 2 widget renderers pass WidgetRendererContractTest (14 assertions each)
- Solar: arc position computation verified at sunrise, noon, sunset
- Solar: countdown formatting verified for hours+minutes and minutes-only
- Shortcuts: empty-state accessibility description verified
- No `rememberCoroutineScope()` in any Render() function
</success_criteria>

<output>
After completion, create `.planning/phases/08-essentials-pack/08-06b-SUMMARY.md`
</output>
