package app.dqxn.android.core.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirebaseCrashReporterTest {

  private lateinit var crashlytics: FirebaseCrashlytics
  private lateinit var reporter: FirebaseCrashReporter

  @BeforeEach
  fun setUp() {
    crashlytics = mockk(relaxed = true)
    reporter = FirebaseCrashReporter(crashlytics)
  }

  @Test
  fun `log delegates to crashlytics log`() {
    reporter.log("test message")

    verify { crashlytics.log("test message") }
  }

  @Test
  fun `logException delegates to crashlytics recordException`() {
    val exception = RuntimeException("test error")

    reporter.logException(exception)

    verify { crashlytics.recordException(exception) }
  }

  @Test
  fun `setKey delegates to crashlytics setCustomKey`() {
    reporter.setKey("widget_id", "clock-1")

    verify { crashlytics.setCustomKey("widget_id", "clock-1") }
  }

  @Test
  fun `setUserId delegates to crashlytics setUserId`() {
    reporter.setUserId("user-42")

    verify { crashlytics.setUserId("user-42") }
  }

  @Test
  fun `setCrashlyticsCollectionEnabled delegates to crashlytics`() {
    every { crashlytics.setCrashlyticsCollectionEnabled(any()) } just runs

    reporter.setCrashlyticsCollectionEnabled(false)

    verify { crashlytics.setCrashlyticsCollectionEnabled(false) }
  }

  @Test
  fun `setCrashlyticsCollectionEnabled enables collection`() {
    every { crashlytics.setCrashlyticsCollectionEnabled(any()) } just runs

    reporter.setCrashlyticsCollectionEnabled(true)

    verify { crashlytics.setCrashlyticsCollectionEnabled(true) }
  }
}
