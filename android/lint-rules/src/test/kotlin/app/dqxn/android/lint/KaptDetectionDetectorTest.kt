package app.dqxn.android.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class KaptDetectionDetectorTest {

    @Test
    fun `positive - kapt dependency triggers error`() {
        lint()
            .files(
                gradle(
                    """
                    plugins {
                        id("com.android.library")
                    }
                    dependencies {
                        kapt("com.google.dagger:hilt-compiler:2.59.2")
                    }
                    """,
                ).indented(),
            )
            .issues(KaptDetectionDetector.ISSUE)
            .allowMissingSdk()
            .ignoreUnknownGradleConstructs()
            .run()
            .expectErrorCount(1)
            .expectContains("KAPT is not allowed")
    }

    @Test
    fun `positive - kotlin-kapt plugin id triggers error`() {
        lint()
            .files(
                gradle(
                    """
                    plugins {
                        id("kotlin-kapt")
                    }
                    """,
                ).indented(),
            )
            .issues(KaptDetectionDetector.ISSUE)
            .allowMissingSdk()
            .ignoreUnknownGradleConstructs()
            .run()
            .expectErrorCount(1)
            .expectContains("KAPT is not allowed")
    }

    @Test
    fun `negative - ksp dependency is clean`() {
        lint()
            .files(
                gradle(
                    """
                    plugins {
                        id("com.android.library")
                    }
                    dependencies {
                        ksp("com.google.dagger:hilt-compiler:2.59.2")
                    }
                    """,
                ).indented(),
            )
            .issues(KaptDetectionDetector.ISSUE)
            .allowMissingSdk()
            .ignoreUnknownGradleConstructs()
            .run()
            .expectClean()
    }
}
