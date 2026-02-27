# Deferred Items - Phase 09

## ~~Pre-existing: Empty stub modules fail `./gradlew test`~~ (Partially Resolved)

**Found during:** 09-06 Task 2 (Full regression gate)
**Modules:** `:feature:diagnostics`, `:feature:onboarding`, `:feature:settings`, `:pack:plus`
**Issue:** Convention plugins wire test dependencies (JUnit5) but these stub modules have no test source files. Gradle's `failOnNoDiscoveredTests` (default true) causes test task failure.
**Resolved:** `:feature:diagnostics` (4 tests), `:feature:onboarding` (4 tests), `:feature:settings` (19 tests) — all now have real test classes and pass.
**Still deferred:** `:pack:plus` — empty stub, no source or tests. Will resolve when Plus pack is implemented.
