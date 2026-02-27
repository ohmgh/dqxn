# Phase 14: UI Visual & Interactive Parity - Context

**Gathered:** 2026-02-27
**Status:** Ready for planning
**Source:** User command context (inline)

<domain>
## Phase Boundary

Restore UI visual and interactive parity with the old codebase after the migration to new architecture. The UI state is broken across multiple areas. This phase fixes all visual and interactive regressions, adapting old patterns to the new architecture where needed.

**Scope areas:**
- Typography (scale, fonts, weight, line height)
- Spacing (padding, margins, grid alignment)
- Animations (edit mode wiggle, widget add/remove, page transitions, sheet transitions, drag snap)
- Graphical assets (icons, backgrounds, decorative elements, density-appropriate rendering)
- OverlayNavHost sheets (present/dismiss, scrim, gesture handling)
- Theme preview states (live preview, revert-on-cancel, timeout)
- Widget preview states (focus overlay toolbar, status overlays)
- Bottom bar (auto-hide, reveal, float-over-canvas, profile icons, edit mode add-widget)
- Splash screen (render, transition to dashboard)

</domain>

<decisions>
## Implementation Decisions

### Old Codebase as Reference
- Use `.planning/oldcodebase/` mapping docs as INDEX into actual source at `../dqxn.old/android/`
- Key mapping files: `feature-dashboard.md` (dashboard UI), `core-libraries.md` (SDK/design tokens), `packs.md` (widget renderers)
- Do NOT copy patterns that conflict with CLAUDE.md — new architecture diverges on state decomposition, Proto DataStore, pack isolation, canvas model
- Extract exact values (timing curves, dp values, color tokens, animation specs) from old source

### Context Pressure Management
- Each plan must be executable with minimal context — self-contained with all necessary values, file paths, and specs
- No plan should require reading the entire old codebase — each plan gets only the specific old-codebase references it needs
- Prefer many small focused plans over fewer large ones

### Claude's Discretion
- How to decompose UI areas into plans (wave structure, grouping)
- Technical approach for each fix (as long as it respects new architecture constraints)
- Test strategy per area (must be automated, zero manual tests)
- Whether to create shared design token infrastructure vs. inline fixes

</decisions>

<specifics>
## Specific Ideas

- Old codebase typography/spacing likely in theme definitions and design system files — check `core-libraries.md` index → `core/design`
- Animation specs (durations, easing curves) in old dashboard feature — check `feature-dashboard.md` index
- OverlayNavHost sheet behavior in old navigation setup — check `feature-dashboard.md` and `app-module.md`
- Bottom bar implementation in old dashboard — check `feature-dashboard.md`
- Splash screen in old app module — check `app-module.md`
- Widget focus/preview states in old pack renderers — check `packs.md`
- Theme preview in old theme system — check `packs.md` and `feature-dashboard.md`

</specifics>

<deferred>
## Deferred Ideas

- F4.11 (spacing tokens, typography scale in Theme Studio) — explicitly deferred in requirements
- F1.19 (HUD mirror mode) — explicitly deferred
- F1.31 (profile auto-switching) — explicitly deferred
- Full design system token extraction as reusable library — only extract what's needed for parity

</deferred>

---

*Phase: 14-ui-visual-interactive-parity*
*Context gathered: 2026-02-27 via user command context*
