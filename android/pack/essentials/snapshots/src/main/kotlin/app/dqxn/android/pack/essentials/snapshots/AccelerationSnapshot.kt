package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Acceleration data snapshot. Gravity-removed longitudinal + optional lateral. */
@DashboardSnapshot(dataType = "acceleration")
@Immutable
public data class AccelerationSnapshot(
  val acceleration: Float,
  val lateralAcceleration: Float?,
  override val timestamp: Long,
) : DataSnapshot
