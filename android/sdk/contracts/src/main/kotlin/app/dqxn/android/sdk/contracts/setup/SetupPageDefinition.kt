package app.dqxn.android.sdk.contracts.setup

// Stub — Plan 03 fleshes out SetupDefinition with 7 concrete subtypes
public data class SetupPageDefinition(
  val id: String,
  val title: String,
  val description: String? = null,
  val definitions: List<SetupDefinition>,
)
