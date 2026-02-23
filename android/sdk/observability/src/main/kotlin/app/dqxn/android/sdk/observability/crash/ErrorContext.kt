package app.dqxn.android.sdk.observability.crash

/**
 * Sealed hierarchy describing the context in which an error occurred (F12.1). Used by
 * [ErrorReporter] and [CrashReporter] to enrich error reports.
 */
public sealed interface ErrorContext {

  /** Error in a dashboard coordinator processing a command. */
  public data class Coordinator(val command: String) : ErrorContext

  /** Error in a widget renderer or its data binding. */
  public data class Widget(val typeId: String, val widgetId: String) : ErrorContext

  /** Error in a data provider. */
  public data class Provider(val sourceId: String) : ErrorContext

  /** Error in a system/platform component. */
  public data class System(val component: String) : ErrorContext
}
