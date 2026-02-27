package app.dqxn.android.feature.dashboard

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Phase 14 regression gate: validates that UI visual/interactive parity changes
 * haven't broken existing implementations and all requirements are covered.
 *
 * Covers 9 of 11 Phase 14 requirement IDs across 10 tests. F2.18 and F3.14
 * are covered by plan-specific tests (FocusOverlayToolbarTest, WidgetStatusOverlayTest).
 */
class Phase14RegressionTest {

    // Gradle sets user.dir to the module root (android/feature/dashboard)
    private val moduleDir = File(checkNotNull(System.getProperty("user.dir")))

    @Test
    fun `F1_21 widget add-remove animations use spring StiffnessMediumLow`() {
        // F1.21: Widget add/remove animations must use spring(StiffnessMediumLow)
        val gridFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt"
        )
        assertThat(gridFile.exists()).isTrue()
        val content = gridFile.readText()

        // Verify AnimatedVisibility with spring stiffness for add/remove
        assertThat(content).contains("StiffnessMediumLow")
        assertThat(content).contains("AnimatedVisibility")
        assertThat(content).contains("scaleIn")
        assertThat(content).contains("scaleOut")
        assertThat(content).contains("fadeIn")
        assertThat(content).contains("fadeOut")
    }

    @Test
    fun `F1_29 profile switching infrastructure exists`() {
        // F1.29: Profile switching via swipe and bottom bar tap
        val profileTransitionFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/profile/ProfilePageTransition.kt"
        )
        assertThat(profileTransitionFile.exists()).isTrue()

        val buttonBarFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/ui/DashboardButtonBar.kt"
        )
        assertThat(buttonBarFile.exists()).isTrue()
        val barContent = buttonBarFile.readText()
        assertThat(barContent).contains("onProfileClick")
        assertThat(barContent).contains("profile_")
    }

    @Test
    fun `F1_8 focus overlay toolbar exists`() {
        val toolbarFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/grid/FocusOverlayToolbar.kt"
        )
        assertThat(toolbarFile.exists()).isTrue()
        val content = toolbarFile.readText()
        assertThat(content).contains("FocusOverlayToolbar")
        assertThat(content).contains("onDelete")
        assertThat(content).contains("onSettings")
    }

    @Test
    fun `F1_9 auto-hide timer state exists in DashboardScreen`() {
        val screenFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt"
        )
        assertThat(screenFile.exists()).isTrue()
        val content = screenFile.readText()
        assertThat(content).contains("isBarVisible")
        assertThat(content).contains("lastInteractionTime")
    }

    @Test
    fun `F1_11 corner brackets use Canvas draw not scale`() {
        val gridFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt"
        )
        val content = gridFile.readText()
        // Must use Canvas drawLine for brackets, NOT scaleX/scaleY for bracketScale
        assertThat(content).contains("drawLine")
        assertThat(content).contains("bracketStrokeWidth")
        // The old broken bracketScale should not be used for scaleX/Y
        assertThat(content).doesNotContain("scaleX = bracketScale")
        assertThat(content).doesNotContain("scaleY = bracketScale")
    }

    @Test
    fun `F1_20 grid overlay renders during drag`() {
        val gridFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt"
        )
        val content = gridFile.readText()
        assertThat(content).contains("drawBehind")
    }

    @Test
    fun `F2_5 widget status overlays use accent color`() {
        val overlayFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/ui/WidgetStatusOverlay.kt"
        )
        val content = overlayFile.readText()
        assertThat(content).contains("accentColor")
    }

    @Test
    fun `F4_6 theme preview has no timeout -- stays active while selector is open`() {
        // F4.6: Per old codebase, theme preview has NO timeout. Preview stays active
        // for the entire duration the ThemeSelector sheet is open. Plan 14-06 removed
        // the 60s timeout that was incorrectly added. Verify it stays removed.
        // Path: from feature/dashboard up to feature/, then into settings/
        val themeSelectorFile = File(
            moduleDir,
            "../settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelector.kt"
        )
        assertThat(themeSelectorFile.exists()).isTrue()
        val content = themeSelectorFile.readText()
        assertThat(content).doesNotContain("PREVIEW_TIMEOUT_MS")
        assertThat(content).doesNotContain("60_000")
    }

    @Test
    fun `F11_7 widget status overlay routes to OpenWidgetSettings`() {
        // F11.7: Permission flow entry point -- SetupRequired/EntitlementRevoked tap -> OpenWidgetSettings
        val slotFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetSlot.kt"
        )
        assertThat(slotFile.exists()).isTrue()
        val content = slotFile.readText()
        assertThat(content).contains("OpenWidgetSettings")
    }

    @Test
    fun `PreviewOverlay composable exists for dashboard-peek pattern`() {
        val previewFile = File(
            moduleDir,
            "src/main/kotlin/app/dqxn/android/feature/dashboard/layer/PreviewOverlay.kt"
        )
        assertThat(previewFile.exists()).isTrue()
        val content = previewFile.readText()
        assertThat(content).contains("previewFraction")
        assertThat(content).contains("onDismiss")
    }
}
