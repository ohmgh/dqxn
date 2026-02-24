package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Battery data snapshot. Level 0-100, temperature in Celsius (null if unavailable). */
@DashboardSnapshot(dataType = "battery")
@Immutable
public data class BatterySnapshot(
  val level: Int,
  val isCharging: Boolean,
  val temperature: Float?,
  override val timestamp: Long,
) : DataSnapshot
