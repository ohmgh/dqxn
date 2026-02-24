package app.dqxn.android.data.layout

import androidx.datastore.core.DataStore
import app.dqxn.android.data.proto.DashboardStoreProto
import app.dqxn.android.data.proto.ProfileCanvasProto
import app.dqxn.android.sdk.common.di.ApplicationScope
import app.dqxn.android.sdk.common.di.IoDispatcher
import app.dqxn.android.sdk.observability.crash.ErrorContext
import app.dqxn.android.sdk.observability.crash.ErrorReporter
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.error
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG = LogTag("LayoutRepository")

/**
 * Proto DataStore-backed implementation of [LayoutRepository]. Keeps an in-memory snapshot
 * ([currentState]) that is updated immediately on mutation. Writes to DataStore are debounced at
 * 500ms via a conflated channel — rapid mutations batch into a single write.
 */
@Singleton
public class LayoutRepositoryImpl
@Inject
constructor(
  private val dashboardDataStore: DataStore<DashboardStoreProto>,
  private val logger: DqxnLogger,
  private val errorReporter: ErrorReporter,
  @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
  @ApplicationScope scope: CoroutineScope,
) : LayoutRepository {

  private val migration = LayoutMigration()

  private val currentState: MutableStateFlow<DashboardStoreProto> =
    MutableStateFlow(FallbackLayout.createFallbackStore())

  private val saveChannel: Channel<Unit> = Channel(Channel.CONFLATED)

  private var initialized = false

  init {
    // Launch the debounced save collector
    scope.launch {
      saveChannel.receiveAsFlow().collect {
        @Suppress("MagicNumber") kotlinx.coroutines.delay(500L)
        persistCurrentState()
      }
    }

    // Load initial state from DataStore
    scope.launch {
      try {
        dashboardDataStore.data.collect { store ->
          if (!initialized) {
            val migrated = applyMigrationIfNeeded(store)
            currentState.value = migrated
            initialized = true
          } else {
            currentState.value = store
          }
        }
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.error(TAG, e) { "Failed to read DashboardStore, using fallback" }
        errorReporter.reportNonFatal(e, ErrorContext.System("LayoutRepository"))
        currentState.value = FallbackLayout.createFallbackStore()
        initialized = true
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Read flows
  // ---------------------------------------------------------------------------

  override val profiles: Flow<ImmutableList<ProfileSummary>>
    get() =
      currentState.map { store ->
        store.profilesList
          .map { p ->
            ProfileSummary(
              profileId = p.profileId,
              displayName = p.displayName,
              sortOrder = p.sortOrder,
            )
          }
          .sortedBy { it.sortOrder }
          .toImmutableList()
      }

  override val activeProfileId: Flow<String>
    get() = currentState.map { it.activeProfileId }

  override fun getActiveProfileWidgets(): Flow<ImmutableList<DashboardWidgetInstance>> =
    currentState.map { store ->
      val profile = store.profilesList.find { it.profileId == store.activeProfileId }
      profile?.widgetsList?.map { DashboardWidgetInstance.fromProto(it) }?.toImmutableList()
        ?: kotlinx.collections.immutable.persistentListOf()
    }

  // ---------------------------------------------------------------------------
  // Profile CRUD
  // ---------------------------------------------------------------------------

  override suspend fun createProfile(displayName: String): String {
    val newId = UUID.randomUUID().toString()
    val newProfile =
      ProfileCanvasProto.newBuilder()
        .setProfileId(newId)
        .setDisplayName(displayName)
        .setSortOrder(currentState.value.profilesCount)
        .build()

    currentState.update { store -> store.toBuilder().addProfiles(newProfile).build() }

    logger.info(TAG) { "Created profile '$displayName' ($newId)" }
    requestSave()
    return newId
  }

  override suspend fun cloneProfile(sourceId: String, displayName: String): String {
    val newId = UUID.randomUUID().toString()

    currentState.update { store ->
      val source =
        store.profilesList.find { it.profileId == sourceId }
          ?: throw IllegalArgumentException("Profile $sourceId not found")

      val clonedWidgets =
        source.widgetsList.map { widget ->
          widget.toBuilder().setId(UUID.randomUUID().toString()).build()
        }

      val clonedProfile =
        ProfileCanvasProto.newBuilder()
          .setProfileId(newId)
          .setDisplayName(displayName)
          .setSortOrder(store.profilesCount)
          .addAllWidgets(clonedWidgets)
          .build()

      store.toBuilder().addProfiles(clonedProfile).build()
    }

    logger.info(TAG) { "Cloned profile '$sourceId' -> '$displayName' ($newId)" }
    requestSave()
    return newId
  }

  override suspend fun switchProfile(targetId: String) {
    currentState.update { store ->
      require(store.profilesList.any { it.profileId == targetId }) {
        "Profile $targetId not found"
      }
      store.toBuilder().setActiveProfileId(targetId).build()
    }

    logger.info(TAG) { "Switched to profile $targetId" }
    requestSave()
  }

  override suspend fun deleteProfile(id: String) {
    currentState.update { store ->
      check(store.profilesCount > 1) { "Cannot delete the last remaining profile" }

      val index = store.profilesList.indexOfFirst { it.profileId == id }
      require(index >= 0) { "Profile $id not found" }

      val builder = store.toBuilder().removeProfiles(index)

      // If deleting the active profile, switch to the first remaining one
      if (store.activeProfileId == id) {
        val remaining = builder.profilesList
        if (remaining.isNotEmpty()) {
          builder.setActiveProfileId(remaining[0].profileId)
        }
      }

      builder.build()
    }

    logger.info(TAG) { "Deleted profile $id" }
    requestSave()
  }

  // ---------------------------------------------------------------------------
  // Widget mutations
  // ---------------------------------------------------------------------------

  override suspend fun addWidget(widget: DashboardWidgetInstance) {
    mutateActiveProfile { profile ->
      profile.toBuilder().addWidgets(widget.toProto()).build()
    }
    requestSave()
  }

  override suspend fun removeWidget(instanceId: String) {
    mutateActiveProfile { profile ->
      val index = profile.widgetsList.indexOfFirst { it.id == instanceId }
      if (index >= 0) {
        profile.toBuilder().removeWidgets(index).build()
      } else {
        profile
      }
    }
    requestSave()
  }

  override suspend fun updateWidget(widget: DashboardWidgetInstance) {
    mutateActiveProfile { profile ->
      val builder = profile.toBuilder()
      val index = profile.widgetsList.indexOfFirst { it.id == widget.instanceId }
      if (index >= 0) {
        builder.setWidgets(index, widget.toProto())
      }
      builder.build()
    }
    requestSave()
  }

  override suspend fun updateWidgetPosition(instanceId: String, position: GridPosition) {
    mutateActiveProfile { profile ->
      val builder = profile.toBuilder()
      val index = profile.widgetsList.indexOfFirst { it.id == instanceId }
      if (index >= 0) {
        val widgetBuilder = profile.widgetsList[index].toBuilder()
        widgetBuilder.setGridX(position.col).setGridY(position.row)
        builder.setWidgets(index, widgetBuilder.build())
      }
      builder.build()
    }
    requestSave()
  }

  override suspend fun updateWidgetSize(instanceId: String, size: GridSize) {
    mutateActiveProfile { profile ->
      val builder = profile.toBuilder()
      val index = profile.widgetsList.indexOfFirst { it.id == instanceId }
      if (index >= 0) {
        val widgetBuilder = profile.widgetsList[index].toBuilder()
        widgetBuilder.setWidthUnits(size.widthUnits).setHeightUnits(size.heightUnits)
        builder.setWidgets(index, widgetBuilder.build())
      }
      builder.build()
    }
    requestSave()
  }

  // ---------------------------------------------------------------------------
  // Internal
  // ---------------------------------------------------------------------------

  private fun mutateActiveProfile(
    transform: (ProfileCanvasProto) -> ProfileCanvasProto,
  ) {
    currentState.update { store ->
      val builder = store.toBuilder()
      val index = store.profilesList.indexOfFirst { it.profileId == store.activeProfileId }
      if (index >= 0) {
        builder.setProfiles(index, transform(store.profilesList[index]))
      }
      builder.build()
    }
  }

  private fun requestSave() {
    saveChannel.trySend(Unit)
  }

  private suspend fun persistCurrentState() {
    withContext(ioDispatcher) {
      try {
        dashboardDataStore.updateData { currentState.value }
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.error(TAG, e) { "Failed to persist dashboard state" }
        errorReporter.reportNonFatal(e, ErrorContext.System("LayoutRepository.persist"))
      }
    }
  }

  private suspend fun applyMigrationIfNeeded(store: DashboardStoreProto): DashboardStoreProto {
    // Empty store -- use fallback
    if (store.profilesCount == 0 && store.activeProfileId.isEmpty()) {
      val fallback = FallbackLayout.createFallbackStore()
      withContext(ioDispatcher) {
        try {
          dashboardDataStore.updateData { fallback }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
          logger.error(TAG, e) { "Failed to write fallback store" }
        }
      }
      return fallback
    }

    return when (val result = migration.migrate(store)) {
      is LayoutMigration.MigrationResult.NoOp -> result.store
      is LayoutMigration.MigrationResult.Success -> {
        logger.info(TAG) { "Migration succeeded to v${LayoutMigration.CURRENT_VERSION}" }
        withContext(ioDispatcher) { dashboardDataStore.updateData { result.store } }
        result.store
      }
      is LayoutMigration.MigrationResult.Reset -> {
        logger.warn(TAG) { "Version gap too large, resetting to defaults" }
        val fallback = FallbackLayout.createFallbackStore()
        withContext(ioDispatcher) { dashboardDataStore.updateData { fallback } }
        fallback
      }
      is LayoutMigration.MigrationResult.Failed -> {
        logger.error(TAG) {
          "Migration failed at v${result.failedAtVersion}, restoring backup"
        }
        // Restore the pre-migration store
        withContext(ioDispatcher) {
          dashboardDataStore.updateData { result.preBackupStore }
        }
        result.preBackupStore
      }
    }
  }
}
