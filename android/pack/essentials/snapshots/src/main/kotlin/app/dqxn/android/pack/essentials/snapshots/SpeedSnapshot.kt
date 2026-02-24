package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Speed data snapshot. Speed in m/s — widgets handle unit conversion. */
@DashboardSnapshot(dataType = "speed")
@Immutable
public data class SpeedSnapshot(
  val speedMps: Float,
  val accuracy: Float?,
  override val timestamp: Long,
) : DataSnapshot
