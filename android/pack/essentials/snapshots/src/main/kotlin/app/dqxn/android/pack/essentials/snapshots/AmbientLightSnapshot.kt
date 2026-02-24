package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Ambient light data snapshot. Category: DARK, DIM, NORMAL, or BRIGHT. */
@DashboardSnapshot(dataType = "ambient_light")
@Immutable
public data class AmbientLightSnapshot(
  val lux: Float,
  val category: String,
  override val timestamp: Long,
) : DataSnapshot
