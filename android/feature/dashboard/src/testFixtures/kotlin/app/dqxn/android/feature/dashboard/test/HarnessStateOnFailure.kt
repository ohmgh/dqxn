package app.dqxn.android.feature.dashboard.test

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

/**
 * JUnit5 [TestWatcher] that auto-dumps harness state as JSON on test failure.
 *
 * Register with `@RegisterExtension` in coordinator tests that use [DashboardTestHarness]. Captures
 * layout widgets, safe mode status, and widget count. Format matches agentic `diagnose-*` JSON
 * structure.
 */
public class HarnessStateOnFailure : TestWatcher {

  /** Set this reference from your test to enable auto-dump on failure. */
  public var harness: DashboardTestHarness? = null

  override fun testFailed(context: ExtensionContext, cause: Throwable?) {
    val h = harness ?: return
    val state = h.layoutCoordinator.layoutState.value
    val safeMode = h.safeModeManager.safeModeActive.value

    val dump = buildString {
      appendLine("=== DashboardTestHarness State Dump ===")
      appendLine("Test: ${context.displayName}")
      appendLine("Failure: ${cause?.message}")
      appendLine()
      appendLine("Layout State:")
      appendLine("  isLoading: ${state.isLoading}")
      appendLine("  activeProfileId: ${state.activeProfileId}")
      appendLine("  widgetCount: ${state.widgets.size}")
      state.widgets.forEach { widget ->
        appendLine(
          "  - ${widget.instanceId}: type=${widget.typeId} " +
            "pos=(${widget.position.col},${widget.position.row}) " +
            "size=(${widget.size.widthUnits}x${widget.size.heightUnits})"
        )
      }
      appendLine()
      appendLine("Safe Mode: active=$safeMode")
    }

    // Print to stderr so it appears in test failure output
    System.err.println(dump)
  }
}
