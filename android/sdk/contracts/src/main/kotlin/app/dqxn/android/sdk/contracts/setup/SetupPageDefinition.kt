package app.dqxn.android.sdk.contracts.setup

import androidx.compose.runtime.Immutable

/** A page within a setup flow, containing a list of [SetupDefinition] items. */
@Immutable
public data class SetupPageDefinition(
  val id: String,
  val title: String,
  val description: String? = null,
  val definitions: List<SetupDefinition>,
)
