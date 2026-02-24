plugins {
  id("dqxn.android.library")
  id("dqxn.android.test")
  id("dqxn.android.hilt")
}

android { namespace = "app.dqxn.android.core.firebase" }

dependencies {
  implementation(project(":sdk:observability"))
  implementation(project(":sdk:analytics"))
  implementation(project(":sdk:common"))

  implementation(libs.kotlinx.collections.immutable)

  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.perf) {
    // firebase-perf pulls protolite-well-known-types:18.0.0 which duplicates classes
    // already in protobuf-javalite:4.30.2 (from :data:proto). Exclude to resolve
    // duplicate class errors during release builds.
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
  }
}
