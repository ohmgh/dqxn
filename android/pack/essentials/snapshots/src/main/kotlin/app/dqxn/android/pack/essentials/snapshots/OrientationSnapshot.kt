package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Orientation data snapshot. Bearing 0-360 degrees, pitch/roll in degrees. */
@DashboardSnapshot(dataType = "orientation")
@Immutable
public data class OrientationSnapshot(
  val bearing: Float,
  val pitch: Float,
  val roll: Float,
  override val timestamp: Long,
) : DataSnapshot
