# Phase 12 Deferred Items

## Pre-existing Issues (Not caused by Phase 12 plans)

### 1. BaselineProfile plugin incompatible with AGP 9
- **Discovered during:** Plan 12-05 (Pitest setup)
- **Committed in:** 77dfee8 (plan 12-01)
- **Issue:** `androidx.baselineprofile` 1.4.1 plugin fails on `:app` with "Module `:app` is not a supported android module" and on `:baselineprofile` with "Extension of type 'TestExtension' does not exist"
- **Impact:** ALL Gradle tasks blocked globally -- no module can compile while this plugin is active
- **Resolution needed:** Either upgrade baselineprofile plugin to a version compatible with AGP 9, or remove the plugin entries from `:app` and `:baselineprofile` until compatibility is available
