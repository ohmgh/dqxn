package app.dqxn.android.data.layout

import app.dqxn.android.data.proto.DashboardStoreProto

/**
 * Chained schema migration for [DashboardStoreProto]. Applies N->N+1 transformers sequentially.
 * V1 is the initial version -- no actual transformers yet. The infrastructure is ready for
 * Phase 7+ schema changes.
 */
public open class LayoutMigration {

  /** Result of a migration attempt. */
  public sealed interface MigrationResult {
    /** Store is already at or above current version -- no migration needed. */
    public data class NoOp(val store: DashboardStoreProto) : MigrationResult

    /** All transformers applied successfully. */
    public data class Success(val store: DashboardStoreProto) : MigrationResult

    /** Version gap too large to safely migrate -- caller should reset to defaults. */
    public data object Reset : MigrationResult

    /**
     * A transformer failed at [failedAtVersion]. [preBackupStore] is the snapshot taken before
     * migration began, enabling the caller to restore cleanly (not the partially-migrated state).
     */
    public data class Failed(
      val failedAtVersion: Int,
      val preBackupStore: DashboardStoreProto,
    ) : MigrationResult
  }

  /** Override in subclasses to change current version for testing. */
  protected open val currentVersion: Int = CURRENT_VERSION

  /** Override in subclasses to change max version gap for testing. */
  protected open val maxVersionGap: Int = MAX_VERSION_GAP

  /** Map of version -> transformer function. Override in subclasses for testing. */
  protected open val transformers: Map<Int, (DashboardStoreProto) -> DashboardStoreProto> =
    emptyMap()

  /**
   * Migrate [store] from its schema_version to [currentVersion]. Transformers are applied
   * sequentially (v0->v1, v1->v2, etc.). If any transformer throws, returns [MigrationResult.Failed]
   * with the original pre-migration store.
   */
  public fun migrate(store: DashboardStoreProto): MigrationResult {
    val storeVersion = store.schemaVersion

    if (storeVersion >= currentVersion) {
      return MigrationResult.NoOp(store)
    }

    val gap = currentVersion - storeVersion
    if (gap > maxVersionGap) {
      return MigrationResult.Reset
    }

    var current = store
    for (version in storeVersion until currentVersion) {
      val transformer = transformers[version] ?: continue
      try {
        current = transformer(current)
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        return MigrationResult.Failed(failedAtVersion = version, preBackupStore = store)
      }
    }

    // Stamp the new version
    current = current.toBuilder().setSchemaVersion(currentVersion).build()
    return MigrationResult.Success(current)
  }

  public companion object {
    public const val CURRENT_VERSION: Int = 1
    public const val MAX_VERSION_GAP: Int = 5
  }
}
