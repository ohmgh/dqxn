package app.dqxn.android.feature.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dqxn.android.core.design.theme.BuiltInThemes
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.command.DashboardCommandBus
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.LayoutCoordinator
import app.dqxn.android.feature.dashboard.coordinator.NotificationCoordinator
import app.dqxn.android.feature.dashboard.coordinator.ProfileCoordinator
import app.dqxn.android.feature.dashboard.coordinator.ThemeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.feature.dashboard.grid.BlankSpaceGestureHandler
import app.dqxn.android.feature.dashboard.grid.WidgetGestureHandler
import app.dqxn.android.sdk.contracts.setup.SetupEvaluator
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import app.dqxn.android.sdk.observability.crash.ErrorReporter
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import app.dqxn.android.sdk.observability.session.EventType
import app.dqxn.android.sdk.observability.session.SessionEvent
import app.dqxn.android.sdk.observability.session.SessionEventEmitter
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.agentic.android.semantics.SemanticsOwnerHolder
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Thin coordinator host that routes [DashboardCommand] to the correct coordinator.
 *
 * Does NOT own state directly -- each coordinator owns its own [StateFlow] slice per the decomposed
 * state architecture. The ViewModel is a wiring point: it initializes all coordinators with the
 * [viewModelScope], processes commands sequentially via a [Channel], and exposes coordinators for
 * composable state collection.
 *
 * SavedStateHandle: saves/restores [activeProfileId]. Edit mode is NOT saved -- always restores to
 * view mode per architecture doc.
 */
@HiltViewModel
public class DashboardViewModel
@Inject
constructor(
  val layoutCoordinator: LayoutCoordinator,
  val editModeCoordinator: EditModeCoordinator,
  val themeCoordinator: ThemeCoordinator,
  val widgetBindingCoordinator: WidgetBindingCoordinator,
  val notificationCoordinator: NotificationCoordinator,
  val profileCoordinator: ProfileCoordinator,
  val widgetRegistry: WidgetRegistry,
  val reducedMotionHelper: ReducedMotionHelper,
  val widgetGestureHandler: WidgetGestureHandler,
  val blankSpaceGestureHandler: BlankSpaceGestureHandler,
  val semanticsOwnerHolder: SemanticsOwnerHolder,
  val userPreferencesRepository: UserPreferencesRepository,
  val dataProviderRegistry: DataProviderRegistry,
  val providerSettingsStore: ProviderSettingsStore,
  val entitlementManager: EntitlementManager,
  val setupEvaluator: SetupEvaluator,
  val pairedDeviceStore: PairedDeviceStore,
  val builtInThemes: BuiltInThemes,
  private val themeProviders: Set<@JvmSuppressWildcards ThemeProvider>,
  private val savedStateHandle: SavedStateHandle,
  private val logger: DqxnLogger,
  private val errorReporter: ErrorReporter,
  private val commandBus: DashboardCommandBus,
  private val sessionEventEmitter: SessionEventEmitter,
) : ViewModel() {

  private val commandChannel = Channel<DashboardCommand>(capacity = 64)

  /** All themes: free built-in themes first, then pack-provided themes. */
  val allThemes: ImmutableList<DashboardThemeDefinition> = run {
    val packThemes = themeProviders
      .flatMap { provider -> provider.getThemes() }
      .filterIsInstance<DashboardThemeDefinition>()
    (builtInThemes.freeThemes + packThemes).toImmutableList()
  }

  /**
   * Simulate free user toggle callback, non-null only when using a stub entitlement manager.
   * Uses runtime method invocation to avoid compile-time coupling to `:app` module.
   */
  val simulateFreeUserToggle: ((Boolean) -> Unit)? = run {
    val mgr = entitlementManager
    val klass = mgr::class
    val grantMethod = klass.members.firstOrNull { it.name == "simulateGrant" }
    val revokeMethod = klass.members.firstOrNull { it.name == "simulateRevocation" }
    val resetMethod = klass.members.firstOrNull { it.name == "reset" }
    if (grantMethod != null && revokeMethod != null && resetMethod != null) {
      { isFreeUser: Boolean ->
        if (isFreeUser) {
          revokeMethod.call(mgr, "themes")
          revokeMethod.call(mgr, "plus")
        } else {
          grantMethod.call(mgr, "themes")
          grantMethod.call(mgr, "plus")
        }
      }
    } else {
      null
    }
  }

  init {
    // Initialize all coordinators with viewModelScope
    layoutCoordinator.initialize(viewModelScope)
    editModeCoordinator.initialize(viewModelScope)
    themeCoordinator.initialize(viewModelScope)
    widgetBindingCoordinator.initialize(viewModelScope)
    notificationCoordinator.initialize(viewModelScope)
    profileCoordinator.initialize(viewModelScope)

    // Restore active profile from SavedStateHandle
    val savedProfileId = savedStateHandle.get<String>(KEY_ACTIVE_PROFILE_ID)
    if (savedProfileId != null) {
      viewModelScope.launch { profileCoordinator.handleSwitchProfile(savedProfileId) }
    }

    // Sequential command processing loop -- exceptions logged but NEVER kill the loop
    viewModelScope.launch {
      for (command in commandChannel) {
        try {
          val elapsed = measureTimeMillis { routeCommand(command) }
          if (elapsed > SLOW_COMMAND_THRESHOLD_MS) {
            logger.warn(TAG) { "Slow command: ${command::class.simpleName} took ${elapsed}ms" }
            // StrictMode.noteSlowCall is available in debug builds at the app level;
            // library modules do not generate BuildConfig by default under AGP 9.
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          logger.warn(TAG) {
            "Command handler exception for ${command::class.simpleName}: ${e.message}"
          }
          errorReporter.reportNonFatal(
            e,
            app.dqxn.android.sdk.observability.crash.ErrorContext.Coordinator(
              command = command::class.simpleName ?: "unknown",
            ),
          )
        }
      }
    }

    // Relay external commands from singleton-scoped bus into sequential channel
    viewModelScope.launch {
      commandBus.commands.collect { command -> commandChannel.send(command) }
    }

    // Auto-bind new widgets when layout changes
    viewModelScope.launch {
      layoutCoordinator.layoutState.collect { layoutState ->
        for (widget in layoutState.widgets) {
          if (widgetBindingCoordinator.activeBindings()[widget.instanceId] == null) {
            widgetBindingCoordinator.bind(widget)
          }
        }
      }
    }

    logger.info(TAG) { "DashboardViewModel initialized" }
  }

  /** Dispatch a command for sequential processing via the command channel. */
  public fun dispatch(command: DashboardCommand) {
    viewModelScope.launch { commandChannel.send(command) }
  }

  private suspend fun routeCommand(command: DashboardCommand) {
    when (command) {
      is DashboardCommand.AddWidget -> {
        layoutCoordinator.handleAddWidget(command.widget)
        widgetBindingCoordinator.bind(command.widget)
        recordSessionEvent(EventType.WIDGET_ADD, "widgetId=${command.widget.instanceId}")
      }
      is DashboardCommand.RemoveWidget -> {
        widgetBindingCoordinator.unbind(command.widgetId)
        layoutCoordinator.handleRemoveWidget(command.widgetId)
        recordSessionEvent(EventType.WIDGET_REMOVE, "widgetId=${command.widgetId}")
      }
      is DashboardCommand.MoveWidget -> {
        layoutCoordinator.handleMoveWidget(command.widgetId, command.newPosition)
        recordSessionEvent(
          EventType.MOVE,
          "widgetId=${command.widgetId}, to=(${command.newPosition.col},${command.newPosition.row})",
        )
      }
      is DashboardCommand.ResizeWidget -> {
        layoutCoordinator.handleResizeWidget(
          command.widgetId,
          command.newSize,
          command.newPosition,
        )
        recordSessionEvent(
          EventType.RESIZE,
          "widgetId=${command.widgetId}, newSize=${command.newSize.widthUnits}x${command.newSize.heightUnits}",
        )
      }
      is DashboardCommand.FocusWidget -> {
        editModeCoordinator.focusWidget(command.widgetId)
        if (command.widgetId != null) {
          recordSessionEvent(EventType.TAP, "widgetId=${command.widgetId}")
        }
      }
      is DashboardCommand.OpenWidgetSettings -> {
        // TODO: Route to widget settings overlay/route. Downstream phases wire actual navigation.
        logger.info(TAG) { "OpenWidgetSettings: widgetId=${command.widgetId}" }
        recordSessionEvent(EventType.TAP, "widgetSettings=${command.widgetId}")
      }
      is DashboardCommand.EnterEditMode -> {
        editModeCoordinator.enterEditMode()
        recordSessionEvent(EventType.EDIT_MODE_ENTER, "")
      }
      is DashboardCommand.ExitEditMode -> {
        editModeCoordinator.exitEditMode()
        recordSessionEvent(EventType.EDIT_MODE_EXIT, "")
      }
      is DashboardCommand.SetTheme -> {
        themeCoordinator.handleSetTheme(command.themeId)
        recordSessionEvent(EventType.THEME_CHANGE, "themeId=${command.themeId}")
      }
      is DashboardCommand.PreviewTheme -> themeCoordinator.handlePreviewTheme(command.theme)
      is DashboardCommand.CycleThemeMode -> themeCoordinator.handleCycleThemeMode()
      is DashboardCommand.WidgetCrash ->
        widgetBindingCoordinator.reportCrash(command.widgetId, command.typeId)
      is DashboardCommand.SwitchProfile -> {
        profileCoordinator.handleSwitchProfile(command.profileId)
        savedStateHandle[KEY_ACTIVE_PROFILE_ID] = command.profileId
        recordSessionEvent(EventType.NAVIGATE, "route=profile/${command.profileId}")
      }
      is DashboardCommand.CreateProfile ->
        profileCoordinator.handleCreateProfile(command.displayName, command.cloneCurrentId)
      is DashboardCommand.DeleteProfile -> profileCoordinator.handleDeleteProfile(command.profileId)
      is DashboardCommand.ResetLayout -> layoutCoordinator.handleResetLayout()
      is DashboardCommand.ToggleStatusBar -> editModeCoordinator.toggleStatusBar()
      is DashboardCommand.SaveCustomTheme -> {
        // Custom theme persistence -- future (JSON files in internal storage)
        // For now, preview the saved theme so it's immediately visible
        themeCoordinator.handlePreviewTheme(command.theme)
        recordSessionEvent(EventType.THEME_CHANGE, "customTheme=${command.theme.themeId}")
      }
      is DashboardCommand.DeleteCustomTheme -> {
        // Custom theme deletion -- future (remove JSON file)
        // Clear preview if deleted theme was being previewed
        val currentPreview = themeCoordinator.themeState.value.previewTheme
        if (currentPreview?.themeId == command.themeId) {
          themeCoordinator.handlePreviewTheme(null)
        }
        recordSessionEvent(EventType.THEME_CHANGE, "deleteCustom=${command.themeId}")
      }
      is DashboardCommand.SetAutoSwitchMode -> {
        themeCoordinator.handleSetAutoSwitchMode(command.mode)
        recordSessionEvent(EventType.THEME_CHANGE, "autoSwitchMode=${command.mode}")
      }
      is DashboardCommand.SetIlluminanceThreshold ->
        themeCoordinator.handleSetIlluminanceThreshold(command.threshold)
    }
  }

  /**
   * Record a session event via [SessionEventEmitter]. The emitter is a no-op if recording is
   * disabled, so this call has zero overhead in production when diagnostics recording is off.
   */
  private fun recordSessionEvent(type: EventType, details: String) {
    sessionEventEmitter.record(
      SessionEvent(
        timestamp = System.currentTimeMillis(),
        type = type,
        details = details,
      ),
    )
  }

  override fun onCleared() {
    super.onCleared()
    widgetBindingCoordinator.destroy()
    logger.info(TAG) { "DashboardViewModel cleared" }
  }

  private companion object {
    val TAG = LogTag("DashboardVM")
    const val KEY_ACTIVE_PROFILE_ID = "activeProfileId"
    const val SLOW_COMMAND_THRESHOLD_MS = 1_000L
  }
}
