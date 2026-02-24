package app.dqxn.android.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class WidgetScopeBypassDetectorTest {

  @Test
  fun `positive - rememberCoroutineScope in widget Render triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speed/SpeedRenderer.kt",
            """
                    package app.dqxn.android.pack.essentials.widgets.speed
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.rememberCoroutineScope
                    class SpeedRenderer {
                        @Composable
                        fun Render() {
                            val scope = rememberCoroutineScope()
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(WidgetScopeBypassDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("LocalWidgetScope.current")
  }

  @Test
  fun `positive - GlobalScope in widget Render triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speed/SpeedRenderer.kt",
            """
                    package app.dqxn.android.pack.essentials.widgets.speed
                    import kotlinx.coroutines.GlobalScope
                    import kotlinx.coroutines.launch
                    class SpeedRenderer {
                        fun doWork() {
                            GlobalScope.launch { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(WidgetScopeBypassDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("GlobalScope")
  }

  @Test
  fun `negative - LocalWidgetScope current in widget Render is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speed/SpeedRenderer.kt",
            """
                    package app.dqxn.android.pack.essentials.widgets.speed
                    import androidx.compose.runtime.Composable
                    class SpeedRenderer {
                        @Composable
                        fun Render() {
                            val scope = LocalWidgetScope.current
                            scope.launch { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(WidgetScopeBypassDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - derivedStateOf in widget Render is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/speed/SpeedRenderer.kt",
            """
                    package app.dqxn.android.pack.essentials.widgets.speed
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.derivedStateOf
                    import androidx.compose.runtime.remember
                    class SpeedRenderer {
                        @Composable
                        fun Render() {
                            val value = remember { derivedStateOf { 42 } }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(WidgetScopeBypassDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - rememberCoroutineScope in non-widget file is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt",
            """
                    package app.dqxn.android.feature.dashboard
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.rememberCoroutineScope
                    class DashboardScreen {
                        @Composable
                        fun Content() {
                            val scope = rememberCoroutineScope()
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(WidgetScopeBypassDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }
}
