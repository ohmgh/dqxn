package app.dqxn.android.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.jupiter.api.Test

class AgenticMainThreadBanDetectorTest {

  @Test
  fun `positive - Dispatchers Main in agentic module triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/core/agentic/PingHandler.kt",
            """
                    package app.dqxn.android.core.agentic
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext
                    class PingHandler {
                        suspend fun handle() {
                            withContext(Dispatchers.Main) { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(AgenticMainThreadBanDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .testModes(TestMode.DEFAULT)
      .run()
      .expectErrorCount(1)
      .expectContains("Dispatchers.Main is banned")
  }

  @Test
  fun `positive - Dispatchers Main immediate in agentic module triggers error`() {
    // Dispatchers.Main.immediate produces 2 errors because UAST sees both
    // the inner Dispatchers.Main and outer Dispatchers.Main.immediate qualified refs.
    // Both are correctly flagged — Main dispatcher is banned regardless.
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/core/agentic/ListHandler.kt",
            """
                    package app.dqxn.android.core.agentic
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext
                    class ListHandler {
                        suspend fun handle() {
                            withContext(Dispatchers.Main.immediate) { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(AgenticMainThreadBanDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .testModes(TestMode.DEFAULT)
      .run()
      .expectErrorCount(2)
      .expectContains("Dispatchers.Main is banned")
  }

  @Test
  fun `negative - Dispatchers Default in agentic module is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/core/agentic/DiagHandler.kt",
            """
                    package app.dqxn.android.core.agentic
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext
                    class DiagHandler {
                        suspend fun handle() {
                            withContext(Dispatchers.Default) { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(AgenticMainThreadBanDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - Dispatchers IO in agentic module is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/core/agentic/DumpHandler.kt",
            """
                    package app.dqxn.android.core.agentic
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext
                    class DumpHandler {
                        suspend fun handle() {
                            withContext(Dispatchers.IO) { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(AgenticMainThreadBanDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - Dispatchers Main in feature module is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardVm.kt",
            """
                    package app.dqxn.android.feature.dashboard
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext
                    class DashboardVm {
                        suspend fun update() {
                            withContext(Dispatchers.Main) { }
                        }
                    }
                    """,
          )
          .indented(),
      )
      .issues(AgenticMainThreadBanDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }
}
