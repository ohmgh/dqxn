package app.dqxn.android.core.firebase

import app.dqxn.android.sdk.observability.crash.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CrashReporter] implementation delegating to Firebase Crashlytics. All Crashlytics interactions
 * are confined to this module -- no other module has Firebase SDK dependencies.
 */
@Singleton
public class FirebaseCrashReporter
@Inject
constructor(
  private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {

  override fun log(message: String) {
    crashlytics.log(message)
  }

  override fun logException(e: Throwable) {
    crashlytics.recordException(e)
  }

  override fun setKey(key: String, value: String) {
    crashlytics.setCustomKey(key, value)
  }

  override fun setUserId(id: String) {
    crashlytics.setUserId(id)
  }

  /** Enables or disables Crashlytics data collection for consent gating. */
  public fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
    crashlytics.setCrashlyticsCollectionEnabled(enabled)
  }
}
