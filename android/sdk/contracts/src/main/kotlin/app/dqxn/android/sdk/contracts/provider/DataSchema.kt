package app.dqxn.android.sdk.contracts.provider

import androidx.compose.runtime.Immutable

@Immutable
public data class DataSchema(
  val fields: List<DataFieldSpec>,
  val stalenessThresholdMs: Long,
)

@Immutable
public data class DataFieldSpec(
  val name: String,
  val typeId: String,
  val unit: String? = null,
  val displayName: String? = null,
)
