package app.dqxn.android.sdk.contracts.widget

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

@Immutable
public data class WidgetData(
  val snapshots: ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>,
  val timestamp: Long,
) {
  public inline fun <reified T : DataSnapshot> snapshot(): T? = snapshots[T::class] as? T

  public fun withSlot(
    type: KClass<out DataSnapshot>,
    snapshot: DataSnapshot,
  ): WidgetData {
    val updated =
      when (val current = snapshots) {
        is PersistentMap -> current.put(type, snapshot)
        else -> current.toPersistentMap().put(type, snapshot)
      }
    return WidgetData(updated, snapshot.timestamp)
  }

  public fun hasData(): Boolean = snapshots.isNotEmpty()

  public companion object {
    public val Empty: WidgetData = WidgetData(persistentMapOf(), 0L)
    public val Unavailable: WidgetData = WidgetData(persistentMapOf(), -1L)
  }
}
