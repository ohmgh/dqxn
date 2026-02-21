# Persistence

> Proto DataStore architecture, corruption handling, schema migration, and preset system.

## Proto DataStore Architecture

Structured data uses Proto DataStore for type-safe, binary-efficient persistence:

```protobuf
message DashboardCanvas {
  int32 schema_version = 1;
  repeated SavedWidget widgets = 2;
}

message SavedWidget {
  string id = 1;
  string type = 2;
  int32 grid_x = 3;
  int32 grid_y = 4;
  int32 width_units = 5;
  int32 height_units = 6;
  string background_style = 7;
  float opacity = 8;
  bool show_border = 9;
  bool has_glow_effect = 10;
  int32 corner_radius_percent = 11;
  int32 rim_size_percent = 12;
  optional string variant = 13;
  map<string, string> settings = 14;  // JSON-encoded values
  repeated string selected_data_source_ids = 15;
  int32 z_index = 16;
}
```

Benefits over JSON-in-Preferences:
- Type-safe schema with generated code
- Binary format (faster serialization, smaller on disk)
- Schema evolution via protobuf field addition (non-breaking)
- Atomic writes (DataStore guarantee)

## Corruption Handling

All DataStore instances MUST have explicit corruption handlers:

```kotlin
val layoutDataStore = DataStoreFactory.create(
    serializer = DashboardCanvasSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        logger.error(LogTag.DATASTORE, "corruption" to true, "file" to "dashboard_layouts") {
            "Layout DataStore corrupted, resetting"
        }
        errorReporter.reportDataStoreCorruption("dashboard_layouts", exception)
        DashboardCanvas.getDefaultInstance()
    },
)
```

This pattern applies to ALL DataStore instances. Each corruption handler resets to its respective default and reports the corruption as a non-fatal error.

## Store Organization

| Store | Type | Contents |
|---|---|---|
| `dashboard_layouts` | Proto | `DashboardCanvas` (widget list + schema version) |
| `paired_devices` | Proto | `Map<definitionId, List<PairedDeviceMetadata>>` |
| `custom_themes` | Proto | User-created theme definitions |
| `user_preferences` | Preferences | Device config, onboarding, themes, status bar, demo mode |
| `provider_settings` | Preferences | Per-provider settings with pack-namespaced keys (`{packId}:{providerId}:{key}`) |
| `connection_events` | Preferences | Rolling list of 50 connection events (JSON) |

## Layout Persistence

Layout saves are debounced at 500ms with atomic writes (write to temp file, rename on success).

## Schema Migration

Versioned via `schema_version` field. Migration transformers registered per version step (N -> N+1). Unknown fields are preserved (protobuf forward compatibility).

## Preset System

JSON preset files define default widget layouts. `PresetLoader` selects region-appropriate presets via `RegionDetector` (timezone-derived country code). Presets generate fresh UUIDs for all widgets on load.

## R8 / ProGuard Rules

Each module owns a `consumer-proguard-rules.pro` file:

```proguard
# :data — keep proto-generated classes (proto schemas are an implementation detail of persistence)
-keep class app.dqxn.data.proto.** { *; }

# :sdk:contracts — keep @Serializable classes
-keepclassmembers class app.dqxn.** {
    @kotlinx.serialization.Serializable <methods>;
}

# :codegen:plugin — keep KSP-generated PackManifest
-keep class **PackManifest { *; }
-keep class **_GeneratedWidgetRef { *; }
-keep class **_GeneratedProviderRef { *; }
```

Release-build smoke test in CI: assemble release APK, install on managed device, verify dashboard loads with at least one widget rendering data.
