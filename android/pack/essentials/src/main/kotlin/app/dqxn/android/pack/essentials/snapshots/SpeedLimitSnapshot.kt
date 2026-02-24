package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Speed limit data snapshot. Source: "user" (static user-configured value). */
@DashboardSnapshot(dataType = "speed_limit")
@Immutable
public data class SpeedLimitSnapshot(
  val speedLimitKph: Float,
  val source: String,
  override val timestamp: Long,
) : DataSnapshot
