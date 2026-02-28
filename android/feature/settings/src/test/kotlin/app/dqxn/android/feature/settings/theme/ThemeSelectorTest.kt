package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import java.io.File
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
  private val freeTheme1 = createTheme("essentials:minimalist", "Minimalist")
  private val freeTheme2 = createTheme("essentials:slate", "Slate")
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
    assertThat(sorted[0].themeId).isEqualTo("essentials:slate")
    assertThat(sorted[1].themeId).isEqualTo("essentials:minimalist")
    // Custom second
    assertThat(sorted[2].themeId).isEqualTo("custom_100")
    // Premium last
    assertThat(sorted[3].themeId).isEqualTo("neon")
    assertThat(sorted[4].themeId).isEqualTo("aurora")
  }

  // --- Star icon tests ---

  @Test
  fun `star icon shown on gated themes`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(premiumTheme1),
          isDark = true,
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = noThemesEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onCreateNewTheme = {},
          onShowToast = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("theme_star_neon", useUnmergedTree = true).assertExists()
  }

  // --- Clone tests ---

  @Test
  fun `clone creates custom copy via long press`() {
    var clonedTheme: DashboardThemeDefinition? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(freeTheme1),
          isDark = true,
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = freeEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = { clonedTheme = it },
          onOpenStudio = {},
          onDeleteCustom = {},
          onCreateNewTheme = {},
          onShowToast = {},
          onBack = {},
        )
      }
    }

    composeTestRule
      .onNodeWithTag("theme_card_essentials:minimalist", useUnmergedTree = true)
      .performLongClick()

    composeTestRule.waitForIdle()

    assertThat(clonedTheme).isNotNull()
    assertThat(clonedTheme!!.themeId).isEqualTo("essentials:minimalist")
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
            isDark = true,
            previewTheme = null,
            customThemeCount = 0,
            entitlementManager = freeEntitlementManager,
            onPreviewTheme = {},
            onApplyTheme = {},
            onClearPreview = { clearPreviewCount++ },
            onCloneToCustom = {},
            onOpenStudio = {},
            onDeleteCustom = {},
            onCreateNewTheme = {},
            onShowToast = {},
            onBack = {},
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
          isDark = true,
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = noThemesEntitlementManager,
          onPreviewTheme = { previewedTheme = it },
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onCreateNewTheme = {},
          onShowToast = {},
          onBack = {},
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

  // --- Source verification tests ---

  @Test
  fun `3-column grid layout verified in source`() {
    val content = readThemeSelectorSource()
    assertThat(content).contains("GridCells.Fixed(3)")
    // Must not use any other column count
    assertThat(content).doesNotContain("GridCells.Fixed(4)")
    assertThat(content).doesNotContain("GridCells.Fixed(2)")
  }

  @Test
  fun `horizontal pager exists with 2 pages`() {
    val content = readThemeSelectorSource()
    assertThat(content).contains("HorizontalPager")
    assertThat(content).contains("rememberPagerState")
  }

  @Test
  fun `no preview timeout in source`() {
    val content = readThemeSelectorSource()
    assertThat(content).doesNotContain("PREVIEW_TIMEOUT_MS")
    assertThat(content).doesNotContain("Preview timed out")
  }

  @Test
  fun `selection border uses highlightColor not accentColor`() {
    val content = readThemeSelectorSource()
    assertThat(content).contains("highlightColor")
    // ThemeCard border must use highlightColor param
    assertThat(content).contains("if (isSelected) highlightColor")
  }

  @Test
  fun `theme cards use aspect ratio 2f`() {
    val content = readThemeSelectorSource()
    assertThat(content).contains("aspectRatio(2f)")
    assertThat(content).doesNotContain("aspectRatio(1.5f)")
  }

  // --- Compose UI tests ---

  @Test
  fun `theme cards show color dots`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = persistentListOf(freeTheme1),
          isDark = true,
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = freeEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onCreateNewTheme = {},
          onShowToast = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("theme_dots_essentials:minimalist", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `themes filtered by isDark parameter`() {
    val lightTheme = createTheme("light-test", "Light Test", isDark = false)
    val darkTheme = createTheme("dark-test", "Dark Test", isDark = true)
    val mixed = persistentListOf(lightTheme, darkTheme)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeSelector(
          allThemes = mixed,
          isDark = false,
          previewTheme = null,
          customThemeCount = 0,
          entitlementManager = freeEntitlementManager,
          onPreviewTheme = {},
          onApplyTheme = {},
          onClearPreview = {},
          onCloneToCustom = {},
          onOpenStudio = {},
          onDeleteCustom = {},
          onCreateNewTheme = {},
          onShowToast = {},
          onBack = {},
        )
      }
    }

    // Light theme visible, dark theme not
    composeTestRule.onNodeWithTag("theme_card_light-test", useUnmergedTree = true).assertExists()
    composeTestRule
      .onNodeWithTag("theme_card_dark-test", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun `pager icons in title bar verified in source`() {
    // Pager icons moved from body to OverlayScaffold actions slot (title bar right edge).
    // Verify via source that the actions lambda contains Palette and Add icons.
    val content = readThemeSelectorSource()
    assertThat(content).contains("actions = {")
    assertThat(content).contains("Icons.Filled.Palette")
    assertThat(content).contains("Icons.Filled.Add")
  }

  // --- Helpers ---

  private fun readThemeSelectorSource(): String {
    val file =
      File(
        System.getProperty("user.dir"),
        "src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelector.kt",
      )
    return file.readText()
  }

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
      highlightColor = Color.Yellow,
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
