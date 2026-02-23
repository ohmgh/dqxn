package app.dqxn.android.sdk.contracts.pack

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable

/**
 * Runtime manifest for a dashboard pack.
 *
 * KSP generates manifests from annotations (Phase 4). This data class is primarily for runtime
 * introspection -- querying available widgets, themes, and data providers per pack.
 */
@Serializable
@Immutable
public data class DashboardPackManifest(
  val packId: String,
  val displayName: String,
  val description: String,
  val version: Int,
  val widgets: ImmutableList<PackWidgetRef>,
  val themes: ImmutableList<PackThemeRef>,
  val dataProviders: ImmutableList<PackDataProviderRef>,
  val category: PackCategory,
  val entitlementId: String?,
)

/** Reference to a widget within a pack manifest. */
@Serializable
@Immutable
public data class PackWidgetRef(
  val typeId: String,
  val displayName: String,
)

/** Reference to a theme within a pack manifest. */
@Serializable
@Immutable
public data class PackThemeRef(
  val themeId: String,
  val displayName: String,
)

/** Reference to a data provider within a pack manifest. */
@Serializable
@Immutable
public data class PackDataProviderRef(
  val sourceId: String,
  val displayName: String,
  val dataType: String,
)

/** Category of a dashboard pack. */
@Serializable
public enum class PackCategory {
  ESSENTIALS,
  PREMIUM,
  REGIONAL,
  DEBUG,
}
