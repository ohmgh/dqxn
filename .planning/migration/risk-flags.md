# Risk Flags

1. **Phase 7 is the bottleneck.** The dashboard decomposition is the highest-risk, highest-effort phase. The old 1040-line ViewModel carries implicit coupling that won't surface until you try to split it. Full agentic debug infrastructure is available to help — use it.

2. **Typed DataSnapshot design may need iteration.** The `@DashboardSnapshot` + KSP approach is designed in docs but untested. The Essentials pack migration (Phase 8) will pressure-test it — be ready to revise contracts. Cross-pack snapshot promotion (from pack to `:sdk:contracts`) is the escape hatch if a snapshot type needs sharing.

3. **sg-erp2 pack depends on a proprietary SDK** (`sg.gov.lta:extol`). Compatibility check runs in Phase 1 (see toolchain checks). If incompatible, remove from Phase 9 scope immediately.

4. **Proto DataStore migration needs a data migration story.** Users of the old app (if any) would lose saved layouts. Given this is pre-launch, probably fine — but confirm.
