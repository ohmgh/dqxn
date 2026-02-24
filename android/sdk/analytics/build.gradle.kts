plugins {
  id("dqxn.android.library")
  id("dqxn.android.test")
}

android { namespace = "app.dqxn.android.sdk.analytics" }

dependencies { api(libs.kotlinx.collections.immutable) }
