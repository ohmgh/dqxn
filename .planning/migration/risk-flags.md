# Risk Flags

1. **Phase 7 is the bottleneck.** The dashboard decomposition is the highest-risk, highest-effort phase. The old 1040-line ViewModel carries implicit coupling that won't surface until you try to split it. Full agentic debug infrastructure is available to help — use it.

2. **Typed DataSnapshot design may need iteration.** The `@DashboardSnapshot` + KSP approach is designed in docs but untested. The Essentials pack migration (Phase 8) will pressure-test it — be ready to revise contracts. Cross-pack snapshot promotion (from pack to `:sdk:contracts`) is the escape hatch if a snapshot type needs sharing.

3. **sg-erp2 pack depends on a proprietary SDK** (`sg.gov.lta:extol`). Compatibility check runs in Phase 1 (see toolchain checks). If incompatible, remove from Phase 9 scope immediately.

4. **Proto DataStore migration needs a data migration story.** Users of the old app (if any) would lose saved layouts. Given this is pre-launch, probably fine — but confirm.

5. **90% coordinator coverage enforced 5 phases late.** The >90% line coverage CI gate for Phase 7's 6 coordinators doesn't enforce until Phase 12. If coordinators ship at 70% coverage, Phase 12 surfaces a retroactive test-writing debt that competes with benchmark work. Mitigation: Phase 7 implementation tracks coverage informally; Phase 8 gate includes a soft check (warning, not blocking) at 80%.

6. **Phase 5 repository tests are the most impactful addition.** Phase 5 delivers 6 DataStore repositories with zero behavioral tests in the original plan. Phase 7 coordinators consume these repositories — fakes in coordinator tests mask repository bugs. The updated Phase 5 now includes CRUD tests for all repositories. If Phase 5 runs long, repository tests are the last thing to cut — they catch bugs that surface 2 phases later in ways that look like coordinator bugs.

7. **Seven areas of nuanced old-codebase UX logic require careful replication.** Documented in [replication-advisory.md](replication-advisory.md). These are hard-to-get-right behaviors where naive reimplementation introduces subtle bugs. Each advisory section is now cross-referenced directly in the affected phase files as a "Replication Advisory References" callout. Summary of phase mappings:

   | Advisory Section | Affected Phases |
   |---|---|
   | §1 Widget Preview Mode | Phase 7 (state machine, animations, gesture locking), Phase 10 (sheet dismissal, viewport coordination) |
   | §2 Jankless Navigation | Phase 7 (Layer 0/1 architecture, OverlayNavHost scaffold), Phase 10 (source-varying transitions, shared element pack card morph) |
   | §3 Theme & Studio Preview | Phase 5 (ThemeAutoSwitchEngine), Phase 7 (ThemeCoordinator displayTheme pattern), Phase 11 (preview lifecycle, race condition fix, studio auto-save) |
   | §4 Source-Varying Transitions | Phase 5 (spring configs to core:design), Phase 7 (widget-level animations, DashboardMotion), Phase 10/11 (route-level transitions) |
   | §5 UI Design System | Phase 3 (DashboardThemeDefinition token model), Phase 5 (core:design spacing/typography/radii/emphasis) |
   | §6 Drag/Snap/Resize/Gestures | Phase 7 (coordinate system, gesture filtering, snap calc, resize mechanics, haptics) |
   | §7 Widget Setup Architecture | Phase 2 (SetupDefinition/SettingDefinition schemas), Phase 8 (evaluator semantics, overlay states), Phase 10 (setup flow UI, BLE state machine, pickers, dispatchers) |
