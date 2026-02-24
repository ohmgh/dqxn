package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Solar data snapshot. sourceMode: "location" or "timezone". */
@DashboardSnapshot(dataType = "solar")
@Immutable
public data class SolarSnapshot(
  val sunriseEpochMillis: Long,
  val sunsetEpochMillis: Long,
  val solarNoonEpochMillis: Long,
  val isDaytime: Boolean,
  val sourceMode: String,
  override val timestamp: Long,
) : DataSnapshot
