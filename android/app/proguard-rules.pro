# ===========================================================================
# DQXN ProGuard / R8 Rules
# ===========================================================================

# --- Proto DataStore generated classes ---
-keep class app.dqxn.android.data.proto.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# --- KSP-generated pack manifests ---
-keep class app.dqxn.android.**.PackManifest { *; }
-keepnames class * extends app.dqxn.android.sdk.contracts.pack.DashboardPackManifest

# --- Widget and data provider implementations (registered via Hilt multibinding) ---
-keep class * implements app.dqxn.android.sdk.contracts.widget.WidgetRenderer { *; }
-keep class * implements app.dqxn.android.sdk.contracts.provider.DataProvider { *; }

# --- kotlinx.serialization ---
-keep,allowobfuscation @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
    *** childSerializers(...);
    *** serialize(...);
    *** deserialize(...);
}

# --- Hilt / Dagger ---
# Hilt-generated code is kept by the Hilt Gradle plugin, but ensure entry points survive.
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
