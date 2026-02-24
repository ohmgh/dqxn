package app.dqxn.android.pack.essentials.snapshots

import androidx.compose.runtime.Immutable
import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
import app.dqxn.android.sdk.contracts.provider.DataSnapshot

/** Time data snapshot. Epoch millis + IANA zone ID. Widgets extract time/date via java.time. */
@DashboardSnapshot(dataType = "time")
@Immutable
public data class TimeSnapshot(
  val epochMillis: Long,
  val zoneId: String,
  override val timestamp: Long,
) : DataSnapshot
