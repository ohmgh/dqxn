package app.dqxn.android.data.layout

import app.dqxn.android.data.proto.DashboardStoreProto
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LayoutMigrationTest {

  private val migration = LayoutMigration()

  private fun storeWithVersion(version: Int): DashboardStoreProto =
    DashboardStoreProto.newBuilder().setSchemaVersion(version).build()

  @Test
  fun `migrate returns NoOp when schema version equals current`() {
    val store = storeWithVersion(LayoutMigration.CURRENT_VERSION)
    val result = migration.migrate(store)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.NoOp::class.java)
    assertThat((result as LayoutMigration.MigrationResult.NoOp).store).isEqualTo(store)
  }

  @Test
  fun `migrate returns NoOp when schema version exceeds current`() {
    val store = storeWithVersion(LayoutMigration.CURRENT_VERSION + 5)
    val result = migration.migrate(store)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.NoOp::class.java)
  }

  @Test
  fun `migrate returns Reset when version gap exceeds MAX_VERSION_GAP`() {
    val store = storeWithVersion(LayoutMigration.CURRENT_VERSION - LayoutMigration.MAX_VERSION_GAP - 1)
    val result = migration.migrate(store)
    assertThat(result).isEqualTo(LayoutMigration.MigrationResult.Reset)
  }

  @Test
  fun `migrate returns Success for v0 to v1 with no transformers`() {
    val store = storeWithVersion(0)
    val result = migration.migrate(store)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.Success::class.java)
    val success = result as LayoutMigration.MigrationResult.Success
    assertThat(success.store.schemaVersion).isEqualTo(LayoutMigration.CURRENT_VERSION)
  }

  @Test
  fun `chained migration applies transformers in sequence`() {
    // Test subclass with v1->v2 and v2->v3 transformers
    val testMigration =
      object : LayoutMigration() {
        override val currentVersion: Int = 3

        override val transformers:
          Map<Int, (DashboardStoreProto) -> DashboardStoreProto> =
          mapOf(
            1 to { store ->
              // v1->v2: set active_profile_id to "migrated-v2"
              store.toBuilder().setActiveProfileId("migrated-v2").build()
            },
            2 to { store ->
              // v2->v3: set auto_switch_enabled to true
              store.toBuilder().setAutoSwitchEnabled(true).build()
            },
          )
      }

    val store = storeWithVersion(1)
    val result = testMigration.migrate(store)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.Success::class.java)
    val migrated = (result as LayoutMigration.MigrationResult.Success).store
    assertThat(migrated.schemaVersion).isEqualTo(3)
    assertThat(migrated.activeProfileId).isEqualTo("migrated-v2")
    assertThat(migrated.autoSwitchEnabled).isTrue()
  }

  @Test
  fun `transformer that throws returns Failed with original pre-migration store`() {
    val testMigration =
      object : LayoutMigration() {
        override val currentVersion: Int = 2

        override val transformers:
          Map<Int, (DashboardStoreProto) -> DashboardStoreProto> =
          mapOf(
            1 to { _ -> error("Simulated migration failure") },
          )
      }

    val original = storeWithVersion(1)
    val result = testMigration.migrate(original)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.Failed::class.java)
    val failed = result as LayoutMigration.MigrationResult.Failed
    assertThat(failed.failedAtVersion).isEqualTo(1)
    assertThat(failed.preBackupStore).isEqualTo(original)
  }

  @Test
  fun `failing transformer preserves original store not partially migrated state`() {
    // v1->v2 succeeds, v2->v3 fails: preBackupStore should be the original v1 input,
    // NOT the intermediate v2 result.
    val testMigration =
      object : LayoutMigration() {
        override val currentVersion: Int = 3

        override val transformers:
          Map<Int, (DashboardStoreProto) -> DashboardStoreProto> =
          mapOf(
            1 to { store ->
              // v1->v2: succeeds, modifies active profile id
              store.toBuilder().setActiveProfileId("intermediate-v2").build()
            },
            2 to { _ ->
              // v2->v3: fails
              error("Simulated v2->v3 failure")
            },
          )
      }

    val original =
      DashboardStoreProto.newBuilder()
        .setSchemaVersion(1)
        .setActiveProfileId("original-v1")
        .build()

    val result = testMigration.migrate(original)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.Failed::class.java)
    val failed = result as LayoutMigration.MigrationResult.Failed
    assertThat(failed.failedAtVersion).isEqualTo(2)
    // Critical: preBackupStore is the ORIGINAL, not the partially-migrated intermediate
    assertThat(failed.preBackupStore.activeProfileId).isEqualTo("original-v1")
    assertThat(failed.preBackupStore.schemaVersion).isEqualTo(1)
  }

  @Test
  fun `migration skips versions without transformers`() {
    // Only has v1->v2 transformer, but currentVersion is 4.
    // Versions v2->v3 and v3->v4 have no transformers -- should be skipped gracefully.
    val testMigration =
      object : LayoutMigration() {
        override val currentVersion: Int = 4

        override val transformers:
          Map<Int, (DashboardStoreProto) -> DashboardStoreProto> =
          mapOf(
            1 to { store ->
              store.toBuilder().setActiveProfileId("migrated").build()
            },
          )
      }

    val store = storeWithVersion(1)
    val result = testMigration.migrate(store)
    assertThat(result).isInstanceOf(LayoutMigration.MigrationResult.Success::class.java)
    val success = result as LayoutMigration.MigrationResult.Success
    assertThat(success.store.schemaVersion).isEqualTo(4)
    assertThat(success.store.activeProfileId).isEqualTo("migrated")
  }
}
