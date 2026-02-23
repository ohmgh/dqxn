# Phase 4: KSP Codegen

**What:** Build-time code generation that packs and agentic depend on.

## `:codegen:plugin`

- KSP processor for `@DashboardWidget` / `@DashboardDataProvider` / `@DashboardSnapshot`
- Generates `PackManifest` implementations
- Generates Hilt multibinding modules (replaces old manual `@Binds @IntoSet`)
- Generates Compose stability config file listing all `@DashboardSnapshot`-annotated types (wired into Compose compiler by `dqxn.pack` convention plugin)
- `typeId` format validation: `{packId}:{widget-name}`
- `@DashboardSnapshot` validation: no duplicate `dataType` strings **within the same module** (KSP runs per-module — cross-module uniqueness is a runtime check in `WidgetRegistry`/`DataProviderRegistry`), `@Immutable` required, only `val` properties, implements `DataSnapshot`

## `:codegen:agentic`

- KSP processor for `@AgenticCommand` annotations
- Generates `AgenticCommandRouter` with command dispatch wiring
- Generates param validation from annotation schema
- Generates `list-commands` schema output (self-describing command registry)
- Compilation error on missing handler (annotated command with no implementation)
- **JVM-vs-Android:** Same constraint as `:codegen:plugin` — `:codegen:agentic` uses `dqxn.kotlin.jvm` (pure JVM) and cannot have a Gradle module dependency on `:core:agentic` (Android library). The processor reads `@AgenticCommand` annotation metadata from `KSAnnotation` in the compilation environment, not via compile dependency. `@AgenticCommand` is defined in `:core:agentic` (Phase 6). **Phase 4 builds the processor structure against expected annotation shapes; it becomes functional when Phase 6 provides the annotation types at consumer compile time.** Phase 4 tests use compile-testing with synthetic annotation declarations
- **Phase ordering note:** `@AgenticCommand` lives in `:core:agentic` (Phase 6), but the processor is built in Phase 4. This is not circular — the processor reads annotation metadata from the consumer's compilation classpath (which includes `:core:agentic` at compile time), not from its own module dependencies

**Ported from old:** `core:plugin-processor` → `:codegen:plugin` (adapt for new annotation shapes, add manifest generation). `core:agentic-processor` → `:codegen:agentic` (expand from simple dispatch to full schema generation). **Warning: old `GeneratedCommandRegistry` from agentic processor is dead code** — `AgenticEngine` explicitly bypasses it and does manual registration because the generator can't handle constructor dependencies. Do not replicate this pattern. New `:codegen:agentic` must generate a router that works with Hilt-injected handler instances, not no-arg constructors.

**Convention plugin wiring check:** Apply `dqxn.pack` to a stub module (no source, just the plugin application). Verify the resolved dependency graph includes all expected `:sdk:*` modules with `implementation` scope and that the KSP-generated Compose stability config file path is wired into the Compose compiler options. This is a 7-phase gap between the plugin (Phase 1) and its first real consumer (Phase 8) — misconfigured auto-wiring would silently propagate to every pack. Note: this check requires all `:sdk:*` modules to exist (Phase 3 deliverables). If Phase 4 runs concurrently with Phase 3, defer this validation to Phase 6 (first `:app` assembly). The KSP processor work in Phase 4 has no dependency on Phase 3; only this validation check does.

**Tests:** KSP processor tests with compile-testing. Verify generated `list-commands` output. Verify compilation failure on malformed `typeId`. Verify `@DashboardSnapshot` rejects: duplicate `dataType`, mutable properties, missing `@Immutable`, non-`DataSnapshot` class.
