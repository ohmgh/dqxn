package app.dqxn.android.feature.dashboard.command

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Sealed interface for all discrete dashboard commands routed through the command channel.
 *
 * Each variant carries an optional [traceId] for correlation through the coordinator pipeline.
 * Continuous gestures (drag, resize) use [MutableStateFlow] instead -- see state-management.md.
 */
public sealed interface DashboardCommand {

  /** Optional trace ID propagated from sender for observability correlation. */
  public val traceId: String?

  public data class AddWidget(
    val widget: DashboardWidgetInstance,
    val profileId: String? = null,
    val addToAllProfiles: Boolean = false,
    override val traceId: String? = null,
  ) : DashboardCommand

  /** Add a widget by type ID — instance construction delegated to the ViewModel/coordinator. */
  public data class AddWidgetByTypeId(
    val typeId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class RemoveWidget(
    val widgetId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class MoveWidget(
    val widgetId: String,
    val newPosition: GridPosition,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class ResizeWidget(
    val widgetId: String,
    val newSize: GridSize,
    val newPosition: GridPosition? = null,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class FocusWidget(
    val widgetId: String?,
    override val traceId: String? = null,
  ) : DashboardCommand

  /** Navigate to the widget settings/setup screen for the given widget. */
  public data class OpenWidgetSettings(
    val widgetId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data object EnterEditMode : DashboardCommand {
    override val traceId: String? = null
  }

  public data object ExitEditMode : DashboardCommand {
    override val traceId: String? = null
  }

  public data class SetTheme(
    val themeId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class PreviewTheme(
    val theme: DashboardThemeDefinition?,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class WidgetCrash(
    val widgetId: String,
    val typeId: String,
    val throwable: Throwable,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class SwitchProfile(
    val profileId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class CreateProfile(
    val displayName: String,
    val cloneCurrentId: String? = null,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class DeleteProfile(
    val profileId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data object ResetLayout : DashboardCommand {
    override val traceId: String? = null
  }

  public data object ToggleStatusBar : DashboardCommand {
    override val traceId: String? = null
  }

  public data object CycleThemeMode : DashboardCommand {
    override val traceId: String? = null
  }

  public data class SaveCustomTheme(
    val theme: DashboardThemeDefinition,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class DeleteCustomTheme(
    val themeId: String,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class SetAutoSwitchMode(
    val mode: AutoSwitchMode,
    override val traceId: String? = null,
  ) : DashboardCommand

  public data class SetIlluminanceThreshold(
    val threshold: Float,
    override val traceId: String? = null,
  ) : DashboardCommand
}
