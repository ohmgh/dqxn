package app.dqxn.android.data.device

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** A single device connection event for diagnostic logging. */
@Serializable
public data class ConnectionEvent(
  val timestamp: Long,
  val deviceMac: String,
  val eventType: String,
  val details: String = "",
)

/**
 * Rolling-window event log for BLE connection events. Maintains at most [MAX_EVENTS] entries,
 * evicting the oldest when the window overflows.
 */
public interface ConnectionEventStore {

  /** Observe all connection events, newest last. */
  public val events: Flow<ImmutableList<ConnectionEvent>>

  /** Record a new event. If the list exceeds [MAX_EVENTS], the oldest event is evicted. */
  public suspend fun recordEvent(event: ConnectionEvent)

  /** Clear all recorded events. */
  public suspend fun clear()

  public companion object {
    public const val MAX_EVENTS: Int = 50
  }
}
