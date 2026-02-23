package app.dqxn.android.sdk.observability.crash

/**
 * Interface for non-fatal error reporting (F12.1). Widget crashes are reported with
 * [WidgetErrorContext] for safe-mode evaluation.
 */
public interface ErrorReporter {
  public fun reportNonFatal(e: Throwable, context: ErrorContext)

  public fun reportWidgetCrash(
    typeId: String,
    widgetId: String,
    context: WidgetErrorContext,
  )
}

/** No-op implementation used when error reporting is not configured. */
public object NoOpErrorReporter : ErrorReporter {
  override fun reportNonFatal(e: Throwable, context: ErrorContext) {}

  override fun reportWidgetCrash(
    typeId: String,
    widgetId: String,
    context: WidgetErrorContext,
  ) {}
}
