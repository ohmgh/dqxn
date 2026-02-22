# DQXN

Modular Android dashboard platform for real-time data display through configurable widgets on a grid canvas. Pack-based plugin architecture: packs register widgets, providers, and themes via contracts; the shell discovers them at runtime via Hilt multibinding.

**Use cases:** Automotive (phone/tablet mounted in vehicle), desk/bedside displays, home automation panels, finance dashboards.
**Tagline:** "Life is a dash. Make it beautiful."
**Stage:** Pre-launch greenfield. Architecture docs finalized, zero implementation code.

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Full technical design: module structure, dependency rules, layers, patterns |
| [arch/](arch/) | Deep-dive docs: state management, Compose performance, plugin system, observability, persistence, testing, build system, platform |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Product requirements: F1-F14 functional, NF1-NF47 non-functional, widget/provider/theme inventories, user flows |
| [MIGRATION.md](MIGRATION.md) | Implementation playbook: per-phase deliverables, porting notes, test lists, integration policy, TDD policy |
| [ROADMAP.md](ROADMAP.md) | GSD phase index with requirement mappings and success criteria |

## Tech Stack

Kotlin 2.3+, Jetpack Compose + Material 3, Hilt + KSP (no KAPT), Proto DataStore + Preferences DataStore, kotlinx-collections-immutable, kotlinx.serialization. compileSdk 36, minSdk 31, targetSdk 36. AGP 9.0.1, Gradle 9.3.1, JDK 25.

## Module Structure

```
android/
  sdk/      — contracts, common, ui, observability, analytics (pack API surface)
  core/     — design, thermal, firebase, agentic (shell internals)
  codegen/  — plugin, agentic (KSP, build-time only)
  data/     — Proto + Preferences DataStore, .proto schemas
  feature/  — dashboard, settings, diagnostics, onboarding
  pack/     — free (+snapshots), plus, themes, demo
  app/      — single-activity entry, DI assembly
```

## Critical Architectural Rules

1. **Pack isolation:** Packs depend on `:sdk:*` and `:pack:*:snapshots` only. Never on `:feature:*` or `:core:*`. Shell imports nothing from packs at compile time.
2. **Decomposed state:** Each coordinator owns its own `StateFlow` slice. No god-object state. Per-widget data via individual flows.
3. **Typed snapshots:** `@DashboardSnapshot`-annotated subtypes per data type. No `Map<String, Any>` boxing. KSP-validated.
4. **60fps with 12+ widgets:** `graphicsLayer` isolation, `LocalWidgetData` + `derivedStateOf` for read deferral, `ImmutableList`/`ImmutableMap` everywhere.
5. **Widget error isolation:** `SupervisorJob` parent for bindings. `WidgetCoroutineScope` via CompositionLocal. Failures report via `widgetStatus`, never propagate.
6. **Dashboard-as-shell:** Dashboard is Layer 0, always present. Overlays navigate on Layer 1.

## Hard Constraints

- No Java. No KAPT. No `GlobalScope`. No `runBlocking` (except tests + debug ContentProvider).
- All DataStore instances `@Singleton` with `ReplaceFileCorruptionHandler`.
- No sliders in settings (HorizontalPager conflict).
- No driving mode at V1 — deferred post-launch as pack-provided feature.
- General-purpose dashboard, not vehicle-first.

## Current State

Phase 1 not started. See [ROADMAP.md](ROADMAP.md) for phase structure, [MIGRATION.md](MIGRATION.md) for implementation details per phase.
