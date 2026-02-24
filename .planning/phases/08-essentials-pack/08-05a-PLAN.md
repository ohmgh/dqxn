---
phase: 08-essentials-pack
plan: "05a"
type: execute
wave: 2
depends_on: ["08-01"]
files_modified:
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockDigitalRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockAnalogRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/date/DateSimpleRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/date/DateStackRenderer.kt
  - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/date/DateGridRenderer.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/ClockDigitalRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/ClockAnalogRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/DateSimpleRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/DateStackRendererTest.kt
  - android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/DateGridRendererTest.kt
autonomous: true
requirements:
  - F5.2
  - F5.3
  - NF-I2

must_haves:
  truths:
    - "ClockDigitalRenderer displays time from TimeSnapshot with configurable seconds and 24h format"
    - "ClockAnalogRenderer draws clock hands at correct angles for any given time"
    - "Date renderers format date from TimeSnapshot with locale-aware formatting"
  artifacts:
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockDigitalRenderer.kt"
      provides: "Digital clock widget"
      contains: "@DashboardWidget"
    - path: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockAnalogRenderer.kt"
      provides: "Analog clock widget with Canvas rendering"
      contains: "drawWithCache"
  key_links:
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockDigitalRenderer.kt"
      to: "LocalWidgetData.current"
      via: "reads TimeSnapshot via CompositionLocal"
      pattern: "LocalWidgetData"
    - from: "android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockAnalogRenderer.kt"
      to: "drawWithCache"
      via: "Canvas rendering with cached draw objects"
      pattern: "drawWithCache"
---

<objective>
Implement 5 clock and date widget renderers with contract tests.

Purpose: Clock and date widgets are the highest-volume widgets — they appear in every default layout. All read from TimeSnapshot via LocalWidgetData.current with derivedStateOf. ClockAnalog uses Canvas drawWithCache for hand rendering.

Output: 5 widget renderers (ClockDigital, ClockAnalog, DateSimple, DateStack, DateGrid) with contract tests + widget-specific behavior tests.
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
@.planning/oldcodebase/packs.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement Clock Digital, Clock Analog, and 3 Date widget renderers</name>
  <files>
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockDigitalRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/clock/ClockAnalogRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/date/DateSimpleRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/date/DateStackRenderer.kt
    android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/date/DateGridRenderer.kt
  </files>
  <action>
    All widgets: package under `app.dqxn.android.pack.essentials.widgets.{subpackage}`. Annotated with `@DashboardWidget(typeId = "essentials:{name}", displayName = "...")`. `@Inject constructor()`. Implement `WidgetRenderer`.

    **Pattern for ALL widgets:**
    - `Render()` reads `LocalWidgetData.current` then `derivedStateOf { widgetData.snapshot<XSnapshot>() }`
    - Handle null snapshot gracefully (no data yet — show placeholder or empty state)
    - `accessibilityDescription(data: WidgetData): String` returns human-readable value (e.g., "12:30 PM")
    - `settingsSchema: List<SettingDefinition<*>>` declares all configurable properties
    - `getDefaults(context: WidgetContext): WidgetDefaults` returns default width/height in grid units
    - `compatibleSnapshots: Set<KClass<out DataSnapshot>>` declares data types
    - `requiredAnyEntitlement: List<String>? = null` (all essentials are free)
    - NF-I2: All numeric display uses `NumberFormat.getInstance(Locale.getDefault())` for decimal separators and locale-aware unit labels from Android string resources

    **ClockDigitalRenderer** — port from old:
    - typeId: `essentials:clock`, displayName: "Clock (Digital)", defaultSize: 10x9
    - compatibleSnapshots: `setOf(TimeSnapshot::class)`
    - Settings: `showSeconds` (Boolean, default false), `use24HourFormat` (Boolean, default true), `showLeadingZero` (Boolean, default true), `timezoneId` (TimezoneSetting, default null = system)
    - `Render()`: extract hour/minute/second from `TimeSnapshot.epochMillis` using `java.time.Instant.ofEpochMilli().atZone(zoneId)`. Large text for HH:mm, smaller for seconds/AM-PM.
    - `SettingsAwareSizer`: wider when seconds enabled (+2 units width)
    - accessibilityDescription: "12:30 PM" or "12:30:45 PM" depending on seconds setting

    **ClockAnalogRenderer** — port from old:
    - typeId: `essentials:clock-analog`, displayName: "Clock (Analog)", defaultSize: 10x10, aspectRatio: 1f
    - compatibleSnapshots: `setOf(TimeSnapshot::class)`
    - Settings: `showTickMarks` (Boolean, default true), `timezoneId` (TimezoneSetting, default null)
    - `Render()`: Canvas-based with `drawWithCache` for static tick marks. Hour hand angle: `(hour % 12 + minute / 60f) * 30f`. Minute hand: `minute * 6f + second / 10f`. Second hand: `second * 6f`.
    - Draw objects (`Path`, `Paint`) via `remember`/`drawWithCache` — never per-frame allocation
    - accessibilityDescription: "Analog clock showing 3:15"

    **DateSimpleRenderer** — port from old:
    - typeId: `essentials:date-simple`, displayName: "Date (Simple)", defaultSize: 8x4
    - compatibleSnapshots: `setOf(TimeSnapshot::class)`
    - Settings: `dateFormat` (DateFormatSetting, default FULL), `timezoneId` (TimezoneSetting, default null)
    - Render: single-line formatted date text
    - accessibilityDescription: "Monday, January 15, 2026"

    **DateStackRenderer** — port from old:
    - typeId: `essentials:date-stack`, displayName: "Date (Stack)", defaultSize: 8x6
    - Same settings as DateSimple. Render: vertically stacked day-of-week, month, day number.

    **DateGridRenderer** — port from old:
    - typeId: `essentials:date-grid`, displayName: "Date (Grid)", defaultSize: 10x6
    - Settings: `timezoneId` (TimezoneSetting, default null)
    - Render: grid layout showing day name, month name, day number, year in a 2x2 grid arrangement
    - accessibilityDescription: "Monday, January 15, 2026"
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:compileDebugKotlin --console=plain 2>&1 | tail -5</automated>
  </verify>
  <done>5 time-family widgets compile with @DashboardWidget annotations, LocalWidgetData.current + derivedStateOf pattern, settings schemas, and accessibility descriptions. ClockAnalogRenderer uses drawWithCache for Canvas rendering.</done>
</task>

<task type="auto">
  <name>Task 2: Contract tests and widget-specific tests for 5 clock/date widgets</name>
  <files>
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/ClockDigitalRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/ClockAnalogRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/DateSimpleRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/DateStackRendererTest.kt
    android/pack/essentials/src/test/kotlin/app/dqxn/android/pack/essentials/widgets/DateGridRendererTest.kt
  </files>
  <action>
    **All 5 test classes extend `WidgetRendererContractTest` (JUnit4, 14 inherited assertions).** Each overrides `createRenderer()` and `createTestWidgetData()` returning `WidgetData.Empty.withSlot(TimeSnapshot::class, testSnapshot)`.

    **Widget-specific tests beyond contract (at least 1 per widget):**

    ClockDigitalRendererTest:
    - accessibilityDescription includes formatted time from TimeSnapshot
    - getDefaults wider when showSeconds enabled (SettingsAwareSizer)

    ClockAnalogRendererTest:
    - `computeHourHandAngle(3, 0, 0)` returns 90f (3 o'clock)
    - `computeHourHandAngle(12, 0, 0)` returns 360f or 0f (12 o'clock)
    - `computeMinuteHandAngle(15, 0)` returns 90f
    - `computeMinuteHandAngle(30, 0)` returns 180f
    - aspectRatio is 1f

    DateSimpleRendererTest:
    - accessibilityDescription contains formatted date

    DateStackRendererTest:
    - accessibilityDescription contains formatted date

    DateGridRendererTest:
    - accessibilityDescription contains formatted date
  </action>
  <verify>
    <automated>cd /Users/ohm/Workspace/dqxn/android && ./gradlew :pack:essentials:testDebugUnitTest --tests "*.ClockDigital*" --tests "*.ClockAnalog*" --tests "*.DateSimple*" --tests "*.DateStack*" --tests "*.DateGrid*" --console=plain 2>&1 | tail -15</automated>
  </verify>
  <done>5 clock/date widget contract test classes pass 14 inherited assertions each. Clock analog hand angles verified at cardinal positions. All date widgets produce locale-aware accessibility descriptions. All widgets use LocalWidgetData + derivedStateOf pattern.</done>
</task>

</tasks>

<verification>
1. `./gradlew :pack:essentials:testDebugUnitTest --tests "*.ClockDigital*" --tests "*.ClockAnalog*" --tests "*.DateSimple*" --tests "*.DateStack*" --tests "*.DateGrid*" --console=plain` — all 5 widget tests pass
2. Each widget has `@DashboardWidget` annotation with correct typeId format
3. All Render() functions use `LocalWidgetData.current` + `derivedStateOf` (not direct parameter)
4. Clock analog hand math verified at known positions
5. NF-I2: locale-aware formatting in date display
</verification>

<success_criteria>
- 5 widget renderers pass WidgetRendererContractTest (14 assertions each)
- Clock analog: hand angle tests for 3:00, 12:00, 6:30 pass
- All date widgets: locale-aware formatting via java.time DateTimeFormatter
- No `rememberCoroutineScope()` in any Render() function (WidgetScopeBypass lint would catch)
</success_criteria>

<output>
After completion, create `.planning/phases/08-essentials-pack/08-05a-SUMMARY.md`
</output>
