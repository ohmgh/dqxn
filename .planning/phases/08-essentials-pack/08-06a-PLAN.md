---
phase: 08-essentials-pack
plan: "06a"
type: execute
wave: 2
depends_on: ["08-01"]
files_modified:
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/compass/CompassRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speedlimit/SpeedLimitCircleRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speedlimit/SpeedLimitRectRenderer.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/CompassRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/SpeedLimitCircleRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/SpeedLimitRectRendererTest.kt
autonomous: true
requirements:
  - F5.5
  - F5.7
  - F5.8
  - NF-I2

must_haves:
  truths:
    - "CompassRenderer rotates needle to correct bearing from OrientationSnapshot"
    - "SpeedLimitCircleRenderer displays European-style circular sign with region-aware unit"
    - "SpeedLimitRectRenderer displays US-style rectangular sign"
  artifacts:
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/compass/CompassRenderer.kt"
      provides: "Compass widget with Canvas rotation"
      contains: "@DashboardWidget"
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speedlimit/SpeedLimitCircleRenderer.kt"
      provides: "European-style speed limit sign"
      contains: "@DashboardWidget"
  key_links:
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/compass/CompassRenderer.kt"
      to: "OrientationSnapshot"
      via: "reads bearing for needle rotation"
      pattern: "OrientationSnapshot"
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speedlimit/SpeedLimitCircleRenderer.kt"
      to: "RegionDetector"
      via: "determines KPH/MPH display"
      pattern: "RegionDetector"
---

<objective>
Implement Compass and 2 SpeedLimit widget renderers with contract tests.

Purpose: All 3 widgets use Canvas-based rendering. Compass rotates a needle via Canvas `rotate()`. SpeedLimit widgets render road sign shapes (European circle, US rectangle) with RegionDetector for unit-aware display. Thematically related: all are driving-oriented display widgets.

Output: 3 widget renderers (CompassRenderer, SpeedLimitCircleRenderer, SpeedLimitRectRenderer) with contract tests + widget-specific rendering behavior tests.
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
@.planning/oldcodebase/packs.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement Compass and 2 SpeedLimit renderers</name>
  <files>
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/compass/CompassRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speedlimit/SpeedLimitCircleRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speedlimit/SpeedLimitRectRenderer.kt
  </files>
  <action>
    All widgets follow the standard base pattern: `@DashboardWidget`, `@Inject constructor()`, `LocalWidgetData.current` + `derivedStateOf`, `accessibilityDescription()`, settings schema, NF-I2 locale-aware formatting.

    **CompassRenderer** — port from old:
    - typeId: `essentials:compass`, displayName: "Compass", defaultSize: 10x10, aspectRatio: 1f
    - compatibleSnapshots: `setOf(OrientationSnapshot::class)`
    - Settings: `showTickMarks` (Boolean, default true), `showCardinalLabels` (Boolean, default true), `showTiltIndicators` (Boolean, default false)
    - Render: Canvas-based with `drawWithCache` for tick marks and cardinal labels (N/S/E/W). Needle rotates via Canvas `rotate()` by `-bearing` degrees (compass rotates opposite to device bearing). Tilt indicators show pitch/roll lines when enabled.
    - Draw objects: remembered `Path` and `Paint` objects — no per-frame allocation
    - accessibilityDescription: "Compass: heading 45 degrees, Northeast"
    - Cardinal direction from bearing: 0=N, 45=NE, 90=E, 135=SE, 180=S, 225=SW, 270=W, 315=NW (use 22.5 degree buckets)

    **SpeedLimitCircleRenderer** — port from old:
    - typeId: `essentials:speedlimit-circle`, displayName: "Speed Limit (Circle)", defaultSize: 8x8, aspectRatio: 1f
    - compatibleSnapshots: `setOf(SpeedLimitSnapshot::class)`
    - Settings: `borderSizePercent` (IntSetting, 0-100, default 10), `speedUnit` (EnumSetting: AUTO/KPH/MPH, default AUTO), `digitColor` (EnumSetting, default AUTO — Japan uses blue digits)
    - Render: Canvas circle with red border, white fill, speed limit number centered. European-style sign. When speedUnit=AUTO, use `RegionDetector.detectSpeedUnit()`. Convert kph to mph if needed: `kph * 0.621371f`.
    - Region-aware: Japan (detected via RegionDetector timezone) → blue digits
    - accessibilityDescription: "Speed limit: 60 km/h"

    **SpeedLimitRectRenderer** — port from old:
    - typeId: `essentials:speedlimit-rect`, displayName: "Speed Limit (Rectangle)", defaultSize: 6x8
    - compatibleSnapshots: `setOf(SpeedLimitSnapshot::class)`
    - Settings: `borderSizePercent` (IntSetting, default 5), `speedUnit` (EnumSetting: AUTO/KPH/MPH, default AUTO)
    - Render: Canvas rectangle with black border, white fill, "SPEED LIMIT" text above, number below. MUTCD (US DOT) style.
    - accessibilityDescription: "Speed limit: 35 mph"
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:compileDebugKotlin --console=plain 2>&1 | tail -5</automated>
  </verify>
  <done>Compass and 2 SpeedLimit widgets compile. Compass uses Canvas rotation with drawWithCache. SpeedLimit widgets use RegionDetector for unit display.</done>
</task>

<task type="auto">
  <name>Task 2: Contract tests and widget-specific tests for Compass and SpeedLimit widgets</name>
  <files>
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/CompassRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/SpeedLimitCircleRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/SpeedLimitRectRendererTest.kt
  </files>
  <action>
    **All 3 test classes extend `WidgetRendererContractTest` (JUnit4, 14 inherited assertions).** Each overrides `createRenderer()` and `createTestWidgetData()`.

    **CompassRendererTest:**
    - `createTestWidgetData()`: `WidgetData.Empty.withSlot(OrientationSnapshot::class, OrientationSnapshot(bearing = 90f, pitch = 5f, roll = -3f, timestamp = 1L))`
    - Widget-specific: `getCardinalDirection(0f)` returns "N"
    - `getCardinalDirection(90f)` returns "E"
    - `getCardinalDirection(180f)` returns "S"
    - `getCardinalDirection(270f)` returns "W"
    - `getCardinalDirection(45f)` returns "NE"
    - `getCardinalDirection(337.5f)` returns "NNW" or "N" depending on bucket granularity
    - Needle rotation angle: `-bearing` (compass rotates opposite)
    - aspectRatio is 1f

    **SpeedLimitCircleRendererTest:**
    - `createTestWidgetData()`: `withSlot(SpeedLimitSnapshot::class, SpeedLimitSnapshot(speedLimitKph = 60f, source = "user", timestamp = 1L))`
    - Widget-specific: accessibilityDescription includes "60" for 60 kph limit
    - Speed unit conversion: 60 kph → "37" when unit is MPH (60 * 0.621371 = 37.28, rounded)
    - aspectRatio is 1f

    **SpeedLimitRectRendererTest:**
    - `createTestWidgetData()`: same as Circle
    - Widget-specific: accessibilityDescription includes "Speed limit"
    - MUTCD style: description mentions mph when RegionDetector returns MPH
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:testDebugUnitTest --tests "*.CompassRendererTest" --tests "*.SpeedLimitCircleRendererTest" --tests "*.SpeedLimitRectRendererTest" --console=plain 2>&1 | tail -15</automated>
  </verify>
  <done>3 widget contract test classes pass 14 inherited assertions each. Compass cardinal direction at all 4 primary bearings verified. SpeedLimit unit conversion kph→mph verified.</done>
</task>

</tasks>

<verification>
1. `./gradlew :pack:essentials:testDebugUnitTest --tests "*.CompassRenderer*" --tests "*.SpeedLimitCircle*" --tests "*.SpeedLimitRect*" --console=plain` — all 3 widget tests pass
2. Compass cardinal direction function returns correct labels for 8+ compass points
3. SpeedLimit unit conversion uses RegionDetector correctly
4. No `rememberCoroutineScope()` in any Render() function
</verification>

<success_criteria>
- 3 widget renderers pass WidgetRendererContractTest (14 assertions each)
- Compass: cardinal direction mapping verified for all 4 primary directions
- SpeedLimit: kph to mph conversion correct (60 kph ~ 37 mph)
- No `rememberCoroutineScope()` in any Render() function
</success_criteria>

<output>
After completion, create `.planning/phases/08-essentials-pack/08-06a-SUMMARY.md`
</output>
