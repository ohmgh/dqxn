package app.dqxn.android.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoHardcodedSecretsDetectorTest {

  @Test
  fun `positive - hardcoded API key triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Config.kt",
            """
                    package app.dqxn.android
                    val API_KEY = "sk-1234567890abcdef"
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("Hardcoded secret detected")
  }

  @Test
  fun `positive - hardcoded secret token const triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Config.kt",
            """
                    package app.dqxn.android
                    object Config {
                        private const val SECRET_TOKEN = "ghp_abcdefghij1234567890"
                    }
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("Hardcoded secret detected")
  }

  @Test
  fun `positive - hardcoded client secret triggers error`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Auth.kt",
            """
                    package app.dqxn.android
                    class Auth {
                        val clientSecret = "a1b2c3d4e5f6g7h8i9j0"
                    }
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectErrorCount(1)
      .expectContains("Hardcoded secret detected")
  }

  @Test
  fun `negative - BuildConfig reference is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Config.kt",
            """
                    package app.dqxn.android
                    val API_KEY = BuildConfig.API_KEY
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - placeholder value is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Config.kt",
            """
                    package app.dqxn.android
                    val API_KEY = "YOUR_KEY_HERE"
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - test file with hardcoded key is clean`() {
    lint()
      .files(
        kotlin(
            "src/test/kotlin/app/dqxn/android/ConfigTest.kt",
            """
                    package app.dqxn.android
                    val API_KEY = "sk-1234567890abcdef"
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - short string in key field is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Config.kt",
            """
                    package app.dqxn.android
                    val API_KEY = "short"
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  @Test
  fun `negative - normal string variable is clean`() {
    lint()
      .files(
        kotlin(
            "src/main/kotlin/app/dqxn/android/Config.kt",
            """
                    package app.dqxn.android
                    val displayName = "This is a normal display name string"
                    """,
          )
          .indented(),
      )
      .issues(NoHardcodedSecretsDetector.ISSUE)
      .allowMissingSdk()
      .allowCompilationErrors()
      .run()
      .expectClean()
  }
}
