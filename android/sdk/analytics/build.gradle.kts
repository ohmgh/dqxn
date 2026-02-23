plugins {
  id("dqxn.android.library")
  id("dqxn.android.test")
}

android { namespace = "app.dqxn.android.sdk.analytics" }

dependencies { implementation(libs.kotlinx.collections.immutable) }
