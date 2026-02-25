package app.dqxn.android

import android.app.Application
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.observability.crash.CrashEvidenceWriter
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
public class DqxnApplication : Application() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  internal interface CrashEntryPoint {
    fun crashRecovery(): CrashRecovery
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  internal interface AnalyticsConsentEntryPoint {
    fun analyticsTracker(): AnalyticsTracker
    fun userPreferencesRepository(): UserPreferencesRepository
  }

  override fun onCreate() {
    super.onCreate()
    initializeAnalyticsConsent()
    installCrashHandler()
  }

  /**
   * NF-P3: Read persisted analytics consent and apply to AnalyticsTracker before any
   * other initialization that might trigger tracking (e.g., SessionLifecycleTracker).
   *
   * Uses runBlocking because this MUST complete before any tracking call fires.
   * Application.onCreate() runs on the main thread before any UI frame is drawn,
   * and the DataStore preferences read is fast (small file). A coroutine that races
   * with SessionLifecycleTracker.onSessionStart() would create a consent gap.
   */
  private fun initializeAnalyticsConsent() {
    val entryPoint = EntryPointAccessors.fromApplication(this, AnalyticsConsentEntryPoint::class.java)
    val tracker = entryPoint.analyticsTracker()
    val prefsRepo = entryPoint.userPreferencesRepository()
    runBlocking {
      val consent = prefsRepo.analyticsConsent.first()
      tracker.setEnabled(consent)
    }
  }

  private fun installCrashHandler() {
    val entryPoint =
      EntryPointAccessors.fromApplication(this, CrashEntryPoint::class.java)
    val crashRecovery = entryPoint.crashRecovery()

    // CrashEvidenceWriter persists last-crash evidence via SharedPreferences.
    val evidencePrefs = getSharedPreferences("crash_evidence", MODE_PRIVATE)
    val evidenceWriter = CrashEvidenceWriter(evidencePrefs)

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        crashRecovery.recordCrash()
      } catch (_: Exception) {
        // Must never interfere with crash handling.
      }
      // Delegate to evidence writer which in turn delegates to the default handler.
      evidenceWriter.uncaughtException(thread, throwable)
    }
  }
}
