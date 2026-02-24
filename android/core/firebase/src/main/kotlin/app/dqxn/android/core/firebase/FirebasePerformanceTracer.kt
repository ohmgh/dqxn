package app.dqxn.android.core.firebase

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight wrapper around Firebase Performance for custom trace spans. Provides start/stop trace
 * lifecycle and performance collection toggle for consent gating.
 */
@Singleton
public class FirebasePerformanceTracer
@Inject
constructor(
  private val firebasePerformance: FirebasePerformance,
) {

  /** Starts a new named trace and returns it. Caller is responsible for calling [stopTrace]. */
  public fun startTrace(name: String): Trace {
    return firebasePerformance.newTrace(name).also { it.start() }
  }

  /** Stops a previously started trace. */
  public fun stopTrace(trace: Trace) {
    trace.stop()
  }

  /** Enables or disables performance data collection for consent gating. */
  public fun setEnabled(enabled: Boolean) {
    firebasePerformance.isPerformanceCollectionEnabled = enabled
  }
}
