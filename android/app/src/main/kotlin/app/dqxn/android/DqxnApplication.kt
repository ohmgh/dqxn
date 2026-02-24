package app.dqxn.android

import android.app.Application
import app.dqxn.android.sdk.observability.crash.CrashEvidenceWriter
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
public class DqxnApplication : Application() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  internal interface CrashEntryPoint {
    fun crashRecovery(): CrashRecovery
  }

  override fun onCreate() {
    super.onCreate()
    installCrashHandler()
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
