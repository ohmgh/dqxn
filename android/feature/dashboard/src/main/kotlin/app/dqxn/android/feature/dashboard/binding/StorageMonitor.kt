package app.dqxn.android.feature.dashboard.binding

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors available storage space and exposes a [StateFlow] indicating whether storage is low.
 *
 * Low storage threshold is 50MB (NF41). Polled every 60 seconds.
 */
@Singleton
public class StorageMonitor
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
) {

  /**
   * Storage check function. Defaults to [StatFs]-based check on the app's data directory.
   * Tests override this to control storage state without mocking final Android APIs.
   */
  internal var storageChecker: () -> Boolean = {
    try {
      val stat = StatFs(context.dataDir.absolutePath)
      stat.availableBytes < LOW_STORAGE_THRESHOLD_BYTES
    } catch (_: Exception) {
      false // If we can't check, assume storage is fine
    }
  }

  private val _isLow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  /** Whether device storage is below the 50MB threshold. */
  public val isLow: StateFlow<Boolean> = _isLow.asStateFlow()

  /**
   * Start monitoring storage. Call once with a long-lived scope (e.g., application scope).
   * Performs an immediate check then polls every 60 seconds.
   */
  public fun startMonitoring(scope: CoroutineScope) {
    scope.launch {
      while (true) {
        _isLow.value = storageChecker()
        delay(POLL_INTERVAL_MS)
      }
    }
  }

  internal companion object {
    internal const val LOW_STORAGE_THRESHOLD_BYTES: Long = 50L * 1024L * 1024L // 50MB
    internal const val POLL_INTERVAL_MS: Long = 60_000L
  }
}
