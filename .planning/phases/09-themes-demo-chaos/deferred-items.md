# Deferred Items - Phase 09

## Pre-existing: Empty stub modules fail `./gradlew test`

**Found during:** 09-06 Task 2 (Full regression gate)
**Modules:** `:feature:diagnostics`, `:feature:onboarding`, `:feature:settings`, `:pack:plus`
**Issue:** Convention plugins wire test dependencies (JUnit5) but these stub modules have no test source files. Gradle's `failOnNoDiscoveredTests` (default true) causes test task failure.
**Impact:** Must exclude these modules when running `./gradlew test` or add placeholder tests.
**Resolution:** Will be naturally resolved when these modules get actual test classes in their respective phases (Phase 10-11 for features, future for pack:plus).
