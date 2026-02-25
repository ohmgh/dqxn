package app.dqxn.android.core.firebase

import android.os.Bundle
import app.dqxn.android.sdk.analytics.AnalyticsEvent
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AnalyticsTracker] implementation delegating to Firebase Analytics. Consent-gated via
 * [setEnabled] -- when disabled, [track] and [setUserProperty] are no-ops.
 */
@Singleton
public class FirebaseAnalyticsTracker
@Inject
constructor(
  private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsTracker {

  private val enabled: AtomicBoolean = AtomicBoolean(false)

  init {
    // NF-P3: Disable Firebase collection from construction. No events fire until
    // explicit setEnabled(true) after consent verification in DqxnApplication.onCreate().
    firebaseAnalytics.setAnalyticsCollectionEnabled(false)
  }

  override fun isEnabled(): Boolean = enabled.get()

  override fun setEnabled(enabled: Boolean) {
    this.enabled.set(enabled)
    firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
  }

  override fun track(event: AnalyticsEvent) {
    if (!enabled.get()) return
    val bundle = event.params.toBundle()
    firebaseAnalytics.logEvent(event.name, bundle)
  }

  override fun setUserProperty(key: String, value: String) {
    if (!enabled.get()) return
    firebaseAnalytics.setUserProperty(key, value)
  }

  override fun resetAnalyticsData() {
    firebaseAnalytics.resetAnalyticsData()
  }
}

/** Converts an [AnalyticsEvent.params] map to a Firebase [Bundle]. */
private fun Map<String, Any>.toBundle(): Bundle {
  val bundle = Bundle(size)
  for ((key, value) in this) {
    when (value) {
      is String -> bundle.putString(key, value)
      is Int -> bundle.putInt(key, value)
      is Long -> bundle.putLong(key, value)
      is Float -> bundle.putFloat(key, value)
      is Double -> bundle.putDouble(key, value)
      is Boolean -> bundle.putBoolean(key, value)
      else -> bundle.putString(key, value.toString())
    }
  }
  return bundle
}
