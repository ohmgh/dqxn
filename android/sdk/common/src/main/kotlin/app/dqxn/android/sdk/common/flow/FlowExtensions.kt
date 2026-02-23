package app.dqxn.android.sdk.common.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach

/**
 * Throttles emissions to at most one per [periodMillis]. Uses [conflate] to drop intermediate
 * values and [delay] to pace emissions. Suitable for frame-rate throttling on API 31-33.
 */
public fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> =
  conflate().onEach { delay(periodMillis) }

/**
 * Catches exceptions in the flow, logging with [tag] (placeholder until Phase 3 observability), and
 * emits [fallback] on error. Full DqxnLogger integration comes in Phase 3.
 */
public fun <T> Flow<T>.catchAndLog(tag: String, fallback: T): Flow<T> = catch { e ->
  // TODO(phase-3): Replace with DqxnLogger.w(tag, "Flow error", e)
  @Suppress("PrintStackTrace") e.printStackTrace()
  emit(fallback)
}
