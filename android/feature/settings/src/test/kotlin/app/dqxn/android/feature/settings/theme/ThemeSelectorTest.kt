package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeSelectorTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val containerTheme = createTheme("container", "Container", isDark = true)

  // -- Test themes --
  private val freeTheme1 = createTheme("minimalist", "Minimalist")
  private val freeTheme2 = createTheme("slate", "Slate")
  private val customTheme1 = createTheme("custom_100", "My Custom")
  private val premiumTheme1 = createTheme("neon", "Neon", requiredEntitlements = setOf("themes"))
  private val premiumTheme2 =
    createTheme("aurora", "Aurora", requiredEntitlements = setOf("themes"))

  private val allThemes =
    persistentListOf(premiumTheme1, customTheme1, freeTheme2, premiumTheme2, freeTheme1)

  private val freeEntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = true
      override fun getActiveEntitlements(): Set<String> = setOf("free", "themes")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  private val noThemesEntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = id != "themes"
      override fun getActiveEntitlements(): Set<String> = setOf("free")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  // --- Ordering tests ---

  @Test
  fun `themes ordered free first then custom then premium`() {
    val sorted = sortThemes(allThemes)

    // Free themes first
    assertThat(sorted[0].themeId).isEqualTo("slate")
    assertThat(sorted[1].themeId).isEqualTo("minimalist")
    // Custom second
    assertThat(sorted[2].themeId).isEqualTo("custom_100")
    // Premium last
    assertThat(sorted[3].themeId).isEqualTo("neon")
    assertThat(sorted[4].themeId).isEqualTo("aurora")
  }

  // --- Preview timeout tests ---

  @Test
  fun `preview timeout clears preview after 60s`() {
    var clearPreviewCalled = false
    var toastMessage: String? = null

    composeTestRule.mainClock.autoAdvance = false

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(freeTheme1),
          previewTheme = freeTheme1,
          customThemeCount = 0,
          entitlementManager = freeEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = { clearPreviewCalled = true },
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onShowToast = { toastMessage = it },
          onClose = {},
        )
      }
    }

    // Advance past the 60s timeout
    composeTestRule.mainClock.advanceTimeBy(PREVIEW_TIMEOUT_MS + 100)
    composeTestRule.waitForIdle()

    assertThat(clearPreviewCalled).isTrue()
    assertThat(toastMessage).isEqualTo("Preview timed out")
  }

  // --- Lock icon tests ---

  @Test
  fun `lock icon shown on gated themes`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(premiumTheme1),
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = noThemesEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onShowToast = {},
          onClose = {},
        )
      }
    }

    composeTestRule
      .onNodeWithTag("theme_lock_neon", useUnmergedTree = true)
      .assertExists()
  }

  // --- Clone tests ---

  @Test
  fun `clone creates custom copy via long press`() {
    var clonedTheme: DashboardThemeDefinition? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(freeTheme1),
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = freeEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = { clonedTheme = it },
          onOpenStudio = {},
          onDeleteCustom = {},
          onShowToast = {},
          onClose = {},
        )
      }
    }

    composeTestRule
      .onNodeWithTag("theme_card_minimalist", useUnmergedTree = true)
      .performLongClick()

    composeTestRule.waitForIdle()

    assertThat(clonedTheme).isNotNull()
    assertThat(clonedTheme!!.themeId).isEqualTo("minimalist")
  }

  // --- Disposal tests ---

  @Test
  fun `preview cleared on dispose`() {
    var clearPreviewCount = 0
    var showSelector by mutableStateOf(true)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        if (showSelector) {
          ThemeSelector(
            allThemes = persistentListOf(freeTheme1),
            previewTheme = null,
            customThemeCount = 0,
            entitlementManager = freeEntitlementManager,
            onPreviewTheme = {},
            onApplyTheme = {},
            onClearPreview = { clearPreviewCount++ },
            onCloneToCustom = {},
            onOpenStudio = {},
            onDeleteCustom = {},
            onShowToast = {},
            onClose = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    // Remove ThemeSelector from composition
    showSelector = false
    composeTestRule.waitForIdle()

    // onClearPreview should have been called at least once (on dispose)
    assertThat(clearPreviewCount).isGreaterThan(0)
  }

  // --- Preview-regardless-of-entitlement tests ---

  @Test
  fun `gated theme is previewable`() {
    var previewedTheme: DashboardThemeDefinition? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(premiumTheme1),
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = noThemesEntitlementManager,
          onPreviewTheme = { previewedTheme = it },
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onShowToast = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    // Advance clock to ensure AnimatedVisibility has fully rendered
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    // Tap gated theme -- should fire onPreviewTheme (not blocked)
    // combinedClickable with onDoubleClick delays onClick by double-click timeout (~300ms)
    composeTestRule
      .onNodeWithTag("theme_card_neon", useUnmergedTree = true)
      .assertExists()
      .performTouchInput { click() }

    // Advance past double-click detection window so onClick fires
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()

    assertThat(previewedTheme).isNotNull()
    assertThat(previewedTheme!!.themeId).isEqualTo("neon")
  }

  // --- Helpers ---

  private fun createTheme(
    themeId: String,
    displayName: String,
    isDark: Boolean = true,
    requiredEntitlements: Set<String>? = null,
  ): DashboardThemeDefinition =
    DashboardThemeDefinition(
      themeId = themeId,
      displayName = displayName,
      isDark = isDark,
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
      requiredAnyEntitlement = requiredEntitlements,
    )

  /** Performs a long click on a node (not a standard API, so we use semantics action). */
  private fun androidx.compose.ui.test.SemanticsNodeInteraction.performLongClick() {
    // Long click not directly available on SemanticsNodeInteraction
    // Use performTouchInput with longClick gesture
    performTouchInput { longClick() }
  }
}
