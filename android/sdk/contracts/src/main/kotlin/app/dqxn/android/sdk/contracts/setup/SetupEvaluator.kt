package app.dqxn.android.sdk.contracts.setup

/**
 * Evaluates setup schema requirements against the current device/permission state.
 *
 * Two variant semantics (implementations deferred to Phase 7/10):
 * - **`evaluate()`** -- Real-time check. Only considers `isDeviceConnected()` for device scan
 *   requirements. Used by widget status overlay to show `Disconnected` when device is away.
 * - **`evaluateWithPersistence()`** -- Checks `pairedDeviceStore.wasPaired()`. A previously paired
 *   device counts as satisfied even if currently disconnected. Used by setup flow to allow
 *   proceeding without requiring the device to be present right now.
 *
 * Both return [SetupResult] for each definition in the schema.
 */
public interface SetupEvaluator {
  /** Evaluates all definitions in the given schema pages (real-time only). */
  public fun evaluate(schema: List<SetupPageDefinition>): List<SetupResult>
}

/** Result of evaluating a single [SetupDefinition]. */
public data class SetupResult(
  val definitionId: String,
  val satisfied: Boolean,
)
