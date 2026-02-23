package app.dqxn.android.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class ModuleBoundaryViolationDetectorTest {

  @Test
  fun `positive - pack importing from feature module triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/SomeWidget.kt",
            """
                    package app.dqxn.android.pack.essentials.widgets
                    import app.dqxn.android.feature.dashboard.SomeClass
                    class SomeWidget
                    """,
          )
          .indented(),
      )
      .issues(ModuleBoundaryViolationDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("Pack modules cannot import from :feature:* modules")
  }

  @Test
  fun `positive - pack importing from core module triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/DesignRef.kt",
            """
                    package app.dqxn.android.pack.essentials
                    import app.dqxn.android.core.design.Theme
                    class DesignRef
                    """,
          )
          .indented(),
      )
      .issues(ModuleBoundaryViolationDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("Pack modules cannot import from :core:* modules")
  }

  @Test
  fun `positive - pack importing from data module triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/DataRef.kt",
            """
                    package app.dqxn.android.pack.essentials
                    import app.dqxn.android.data.LayoutRepository
                    class DataRef
                    """,
          )
          .indented(),
      )
      .issues(ModuleBoundaryViolationDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("Pack modules cannot import from the :data module")
  }

  @Test
  fun `negative - pack importing from sdk is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/SomeWidget.kt",
            """
                    package app.dqxn.android.pack.essentials
                    import app.dqxn.android.sdk.contracts.WidgetRenderer
                    class SomeWidget
                    """,
          )
          .indented(),
      )
      .issues(ModuleBoundaryViolationDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - feature importing from core is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt",
            """
                    package app.dqxn.android.feature.dashboard
                    import app.dqxn.android.core.design.Theme
                    class DashboardScreen
                    """,
          )
          .indented(),
      )
      .issues(ModuleBoundaryViolationDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - pack importing from own snapshots submodule is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/SpeedWidget.kt",
            """
                    package app.dqxn.android.pack.essentials.widgets
                    import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
                    class SpeedWidget
                    """,
          )
          .indented(),
      )
      .issues(ModuleBoundaryViolationDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }
}
