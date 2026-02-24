---
phase: 08-essentials-pack
plan: "05b"
type: execute
wave: 2
depends_on: ["08-01"]
files_modified:
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/battery/BatteryRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/ambientlight/AmbientLightRenderer.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/BatteryRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/AmbientLightRendererTest.kt
autonomous: true
requirements:
  - F5.4
  - F5.6
  - F5.10
  - NF-I2

must_haves:
  truths:
    - "BatteryRenderer displays level, charging state, and optional temperature"
    - "AmbientLightRenderer shows lux level with category using InfoCardLayout"
  artifacts:
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/battery/BatteryRenderer.kt"
      provides: "Greenfield battery widget"
      contains: "@DashboardWidget"
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/ambientlight/AmbientLightRenderer.kt"
      provides: "Ambient light info card widget"
      contains: "@DashboardWidget"
  key_links:
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/battery/BatteryRenderer.kt"
      to: "LocalWidgetData.current"
      via: "reads BatterySnapshot via CompositionLocal"
      pattern: "LocalWidgetData"
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/ambientlight/AmbientLightRenderer.kt"
      to: "InfoCardLayout"
      via: "uses InfoCardLayout for display"
      pattern: "InfoCardLayout"
---

<objective>
Implement Battery and AmbientLight widget renderers with contract tests.

Purpose: Both widgets use InfoCardLayout from `:sdk:ui` to display sensor data. Battery is greenfield (no old codebase equivalent). AmbientLight is a port. Both follow the standard LocalWidgetData + derivedStateOf pattern.

Output: 2 widget renderers (BatteryRenderer, AmbientLightRenderer) with contract tests + widget-specific behavior tests.
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
@android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/LocalWidgetData.kt
@android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardLayout.kt
@.planning/oldcodebase/packs.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement Battery and AmbientLight widget renderers</name>
  <files>
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/battery/BatteryRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/ambientlight/AmbientLightRenderer.kt
  </files>
  <action>
    Both widgets: package under `app.dqxn.android.pack.essentials.widgets.{subpackage}`. Annotated with `@DashboardWidget(typeId = "essentials:{name}", displayName = "...")`. `@Inject constructor()`. Implement `WidgetRenderer`. Follow the standard pattern: `LocalWidgetData.current` + `derivedStateOf`, null-safe snapshot handling, `accessibilityDescription()`, settings schema, NF-I2 locale-aware formatting.

    **BatteryRenderer** — GREENFIELD:
    - typeId: `essentials:battery`, displayName: "Battery", defaultSize: 6x6
    - compatibleSnapshots: `setOf(BatterySnapshot::class)`
    - Settings: `showPercentage` (Boolean, default true), `showTemperature` (Boolean, default false), `chargingIndicator` (Boolean, default true)
    - Render: uses `InfoCardLayout` from `:sdk:ui`. Shows battery level as large number, charging icon when charging, temperature when enabled.
    - accessibilityDescription: "Battery: 75%, Charging" or "Battery: 45%"

    **AmbientLightRenderer** — port from old:
    - typeId: `essentials:ambient-light`, displayName: "Ambient Light", defaultSize: 8x8
    - compatibleSnapshots: `setOf(AmbientLightSnapshot::class)`
    - Settings: info card layout options (from `InfoCardSettings`)
    - Render: uses `InfoCardLayout`. Shows lux value and category label (DARK/DIM/NORMAL/BRIGHT). Light bulb icon via Canvas.
    - accessibilityDescription: "Ambient Light: 350 lux, Normal"
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:compileDebugKotlin --console=plain 2>&1 | tail -5</automated>
  </verify>
  <done>BatteryRenderer and AmbientLightRenderer compile with @DashboardWidget annotations, InfoCardLayout usage, and LocalWidgetData + derivedStateOf pattern.</done>
</task>

<task type="auto">
  <name>Task 2: Contract tests and widget-specific tests for Battery and AmbientLight</name>
  <files>
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/BatteryRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/AmbientLightRendererTest.kt
  </files>
  <action>
    **Both test classes extend `WidgetRendererContractTest` (JUnit4, 14 inherited assertions).**

    **BatteryRendererTest:**
    - `createTestWidgetData()`: `WidgetData.Empty.withSlot(BatterySnapshot::class, BatterySnapshot(level = 75, isCharging = true, temperature = 28.5f, timestamp = 1L))`
    - Widget-specific:
      - accessibilityDescription includes "75%" for 75% battery
      - accessibilityDescription includes "Charging" when isCharging=true
      - accessibilityDescription excludes temperature when showTemperature default=false

    **AmbientLightRendererTest:**
    - `createTestWidgetData()`: `WidgetData.Empty.withSlot(AmbientLightSnapshot::class, AmbientLightSnapshot(lux = 350f, category = "NORMAL", timestamp = 1L))`
    - Widget-specific:
      - accessibilityDescription includes lux value and category
      - accessibilityDescription with category "BRIGHT" for 1000 lux
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:testDebugUnitTest --tests "*.BatteryRendererTest" --tests "*.AmbientLightRendererTest" --console=plain 2>&1 | tail -15</automated>
  </verify>
  <done>Battery and AmbientLight contract tests pass 14 inherited assertions each. Battery accessibility includes level and charging state. AmbientLight accessibility includes lux and category.</done>
</task>

</tasks>

<verification>
1. `./gradlew :pack:essentials:testDebugUnitTest --tests "*.BatteryRendererTest" --tests "*.AmbientLightRendererTest" --console=plain` — both widget tests pass
2. Each widget has `@DashboardWidget` annotation with correct typeId format
3. Both Render() functions use `LocalWidgetData.current` + `derivedStateOf`
4. Both use InfoCardLayout from `:sdk:ui`
5. NF-I2: locale-aware formatting in battery level display
</verification>

<success_criteria>
- 2 widget renderers pass WidgetRendererContractTest (14 assertions each)
- Battery: accessibility description reflects level and charging state
- AmbientLight: accessibility description includes lux value and category
- No `rememberCoroutineScope()` in any Render() function
</success_criteria>

<output>
After completion, create `.planning/phases/08-essentials-pack/08-05b-SUMMARY.md`
</output>
