package app.dqxn.android.sdk.contracts.status

/**
 * Overlay state for a widget cell, determining what renders on top of the widget.
 *
 * 8 variants:
 * - [Ready] -- normal operation, no overlay
 * - [SetupRequired] -- setup incomplete (scrim 0.60)
 * - [ConnectionError] -- provider connected but erroring (scrim 0.30)
 * - [Disconnected] -- known device not currently connected (scrim 0.15)
 * - [EntitlementRevoked] -- user lost access (scrim 0.60)
 * - [ProviderMissing] -- no provider bound for data type (scrim 0.60)
 * - [DataTimeout] -- first emission or subscriber timeout exceeded
 * - [DataStale] -- data age exceeds staleness threshold
 *
 * Icon names (`String`) replace old `ImageVector` -- resolution to `ImageVector` happens in
 * `:sdk:ui` (Phase 3) via a lookup utility.
 */
public sealed interface WidgetRenderState {

  public data object Ready : WidgetRenderState

  /**
   * Setup incomplete.
   *
   * [requirementType] classifies the entry point:
   * - `"permission"` -> permission-only flow
   * - `"hardware"` -> full setup flow
   * - `"onboarding"` / `null` -> full setup flow
   */
  public data class SetupRequired(
    val requirementType: String? = null,
    val iconName: String? = null,
    val message: String? = null,
  ) : WidgetRenderState

  public data class ConnectionError(
    val message: String? = null,
    val iconName: String? = null,
  ) : WidgetRenderState

  public data object Disconnected : WidgetRenderState

  public data class EntitlementRevoked(
    val upgradeEntitlement: String,
    val iconName: String? = null,
  ) : WidgetRenderState

  public data object ProviderMissing : WidgetRenderState

  public data class DataTimeout(
    val message: String? = null,
  ) : WidgetRenderState

  public data object DataStale : WidgetRenderState
}
