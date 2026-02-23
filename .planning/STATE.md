# Project State

## Current Position

- **Phase:** 1 — Build System Foundation
- **Current Plan:** 2 of 4
- **Milestone:** V1 Launch
- **Next action:** Phase 1, Plan 02 — Module stubs
- **Last session:** 2026-02-23T16:52:42Z
- **Stopped at:** Completed 01-01-PLAN.md

## Progress

| Phase | Status | Notes |
|---|---|---|
| 1. Build System Foundation | In Progress (1/4 plans) | Plan 01 complete: Gradle infrastructure + convention plugins |
| 2. SDK Contracts + Common | Pending | |
| 3. SDK Observability + Analytics + UI | Pending | Concurrent with Phase 4 |
| 4. KSP Codegen | Pending | Concurrent with Phase 3 |
| 5. Core Infrastructure | Pending | |
| 6. Deployable App + Agentic | Pending | First on-device deployment |
| 7. Dashboard Shell | Pending | Highest risk phase |
| 8. Essentials Pack | Pending | Architecture validation gate |
| 9. Themes, Demo + Chaos | Pending | Depends on Phases 8, 10 (SetupSheet UI required for sg-erp2 BLE device pairing) |
| 10. Settings Foundation + Setup UI | Pending | Unblocks sg-erp2 pairing |
| 11. Theme UI + Diagnostics + Onboarding | Pending | Concurrent with Phase 9 |
| 12. CI Gates + Benchmarking | Pending | Concurrent with Phases 9-11 |
| 13. E2E Integration + Launch Polish | Pending | Convergence point |

## Decisions

Key decisions accumulated during architecture phase — full table in `DECISIONS.md` (89 entries). Highlights:

- **Per-profile dashboards** over per-widget visibility (sparse layouts, gesture mismatch)
- **Unbounded canvas** with no-straddle snap and no automatic relocation
- **Driving mode deferred** post-launch — DQXN is general-purpose, not vehicle-first
- **Essentials pack** renamed from "free" (packId=`essentials`, entitlement tier stays `free`)
- **Phase 10 decomposed** from original "Features + Polish" into 4 phases (10-13) for settings, theme UI, CI, and E2E

### Phase 1 Decisions

- **Kotlin 2.3.10** over 2.3.0 (latest stable patch)
- **KSP 2.3.6** with new simplified versioning scheme (no longer Kotlin-version-prefixed)
- **Single shared libs.versions.toml** between root and build-logic (via versionCatalogs create/from)
- **JDK 25 toolchain** with Kotlin JVM_24 fallback (Kotlin 2.3.10 does not support JVM 25 target yet)

## Performance Metrics

| Phase-Plan | Duration | Tasks | Files |
|---|---|---|---|
| 01-01 | 12min | 2 | 20 |

## Context

- All architecture docs finalized under `.planning/`: ARCHITECTURE.md, REQUIREMENTS.md, DECISIONS.md, ROADMAP.md, MIGRATION.md (split into per-phase files)
- Old codebase mapped in `.planning/oldcodebase/` (8 docs, ~6000 lines)
- Replication advisory at `.planning/migration/replication-advisory.md` — 7 hard-to-replicate UX areas cross-referenced in phase files and risk-flags.md
- Build infrastructure established: Gradle 9.3.1, AGP 9.0.1, Kotlin 2.3.10, all convention plugins compiling
