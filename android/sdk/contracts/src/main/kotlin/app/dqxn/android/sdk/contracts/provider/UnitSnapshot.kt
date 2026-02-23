package app.dqxn.android.sdk.contracts.provider

import androidx.compose.runtime.Immutable

/** Sentinel snapshot for action-only providers that have no meaningful data flow. */
@Immutable public data class UnitSnapshot(override val timestamp: Long) : DataSnapshot
