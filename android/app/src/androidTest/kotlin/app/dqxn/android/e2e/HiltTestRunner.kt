package app.dqxn.android.e2e

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom JUnit runner that creates [HiltTestApplication] instead of the production application.
 *
 * Required for `@HiltAndroidTest` instrumented tests. Configured in `app/build.gradle.kts` via
 * `testInstrumentationRunner`.
 */
public class HiltTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
    return super.newApplication(cl, HiltTestApplication::class.java.name, context)
  }
}
