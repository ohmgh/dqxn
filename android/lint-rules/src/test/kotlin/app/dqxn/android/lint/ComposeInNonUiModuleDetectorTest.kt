package app.dqxn.android.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class ComposeInNonUiModuleDetectorTest {

    @Test
    fun `positive - compose material3 in sdk common triggers error`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/sdk/common/SomeUtil.kt",
                    """
                    package app.dqxn.android.sdk.common
                    import androidx.compose.material3.Text
                    class SomeUtil
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectErrorCount(1)
            .expectContains("not allowed in a non-UI module")
    }

    @Test
    fun `positive - compose foundation in data module triggers error`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/data/SomeRepo.kt",
                    """
                    package app.dqxn.android.data
                    import androidx.compose.foundation.layout.Box
                    class SomeRepo
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectErrorCount(1)
            .expectContains("not allowed in a non-UI module")
    }

    @Test
    fun `positive - compose UI in core thermal triggers error`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/core/thermal/ThermalManager.kt",
                    """
                    package app.dqxn.android.core.thermal
                    import androidx.compose.ui.Modifier
                    class ThermalManager
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectErrorCount(1)
            .expectContains("not allowed in a non-UI module")
    }

    @Test
    fun `positive - non-allowed compose import in sdk contracts triggers error`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/sdk/contracts/SomeContract.kt",
                    """
                    package app.dqxn.android.sdk.contracts
                    import androidx.compose.material3.Text
                    interface SomeContract
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectErrorCount(1)
            .expectContains("not allowed in :sdk:contracts")
    }

    @Test
    fun `negative - compose in sdk ui is clean`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/sdk/ui/WidgetContainer.kt",
                    """
                    package app.dqxn.android.sdk.ui
                    import androidx.compose.material3.Text
                    class WidgetContainer
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectClean()
    }

    @Test
    fun `negative - allowed runtime annotations in sdk contracts is clean`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/sdk/contracts/DataSnapshot.kt",
                    """
                    package app.dqxn.android.sdk.contracts
                    import androidx.compose.runtime.Immutable
                    import androidx.compose.runtime.Stable
                    import androidx.compose.runtime.Composable
                    interface DataSnapshot
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectClean()
    }

    @Test
    fun `negative - compose in pack module is clean`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/pack/essentials/widgets/SpeedRenderer.kt",
                    """
                    package app.dqxn.android.pack.essentials.widgets
                    import androidx.compose.material3.Text
                    class SpeedRenderer
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectClean()
    }

    @Test
    fun `negative - compose in feature module is clean`() {
        lint()
            .files(
                kotlin(
                    "src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt",
                    """
                    package app.dqxn.android.feature.dashboard
                    import androidx.compose.material3.Text
                    class DashboardScreen
                    """,
                ).indented(),
            )
            .issues(ComposeInNonUiModuleDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectClean()
    }
}
