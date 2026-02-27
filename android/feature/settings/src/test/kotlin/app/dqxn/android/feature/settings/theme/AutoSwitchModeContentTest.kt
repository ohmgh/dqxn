package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.Entitlements
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoSwitchModeContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val containerTheme =
    DashboardThemeDefinition(
      themeId = "container",
      displayName = "Container",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      highlightColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  /** No themes entitlement — premium badges should appear. */
  private val noThemesManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = id != Entitlements.THEMES
      override fun getActiveEntitlements(): Set<String> = setOf(Entitlements.FREE)
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  /** Has themes entitlement — no premium badges. */
  private val themesManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = true
      override fun getActiveEntitlements(): Set<String> =
        setOf(Entitlements.FREE, Entitlements.THEMES)
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  private fun setContent(
    selectedMode: AutoSwitchMode = AutoSwitchMode.SYSTEM,
    entitlementManager: EntitlementManager = noThemesManager,
  ) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        AutoSwitchModeContent(
          selectedMode = selectedMode,
          illuminanceThreshold = 100f,
          entitlementManager = entitlementManager,
          onModeSelected = {},
          onIlluminanceThresholdChanged = {},
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun `SOLAR_AUTO shows premium badge when not entitled`() {
    setContent(entitlementManager = noThemesManager)

    composeTestRule
      .onNodeWithTag("premium_badge_SOLAR_AUTO", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `ILLUMINANCE_AUTO shows premium badge when not entitled`() {
    setContent(entitlementManager = noThemesManager)

    composeTestRule
      .onNodeWithTag("premium_badge_ILLUMINANCE_AUTO", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `premium badges hidden when entitled`() {
    setContent(entitlementManager = themesManager)

    composeTestRule
      .onNodeWithTag("premium_badge_SOLAR_AUTO", useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule
      .onNodeWithTag("premium_badge_ILLUMINANCE_AUTO", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun `illuminance control always visible`() {
    setContent(selectedMode = AutoSwitchMode.SYSTEM)

    composeTestRule
      .onNodeWithTag("illuminance_control", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `illuminance control present for all modes`() {
    setContent(selectedMode = AutoSwitchMode.LIGHT)

    composeTestRule
      .onNodeWithTag("illuminance_control", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `each mode has icon`() {
    setContent()

    for (mode in AutoSwitchMode.entries) {
      composeTestRule
        .onNodeWithTag("auto_switch_icon_${mode.name}", useUnmergedTree = true)
        .assertExists()
    }
  }

  @Test
  fun `each mode has description`() {
    setContent()

    val descriptions = listOf(
      "Always use light theme",
      "Always use dark theme",
      "Match Android system dark mode",
      "Automatically switch at sunrise and sunset",
      "Automatically switch based on environment brightness",
    )
    for (desc in descriptions) {
      composeTestRule
        .onNode(hasText(desc, substring = false), useUnmergedTree = true)
        .assertExists()
    }
  }

  @Test
  fun `cards have radio button role`() {
    setContent()

    val radioButtonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    for (mode in AutoSwitchMode.entries) {
      composeTestRule
        .onNode(
          radioButtonRole and androidx.compose.ui.test.hasTestTag("auto_switch_mode_${mode.name}"),
          useUnmergedTree = true,
        )
        .assertExists()
    }
  }

  @Test
  fun `moon and sun icons present`() {
    setContent(selectedMode = AutoSwitchMode.ILLUMINANCE_AUTO, entitlementManager = themesManager)

    composeTestRule
      .onNodeWithTag("illuminance_moon", useUnmergedTree = true)
      .assertExists()
    composeTestRule
      .onNodeWithTag("illuminance_sun", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `threshold label text`() {
    setContent(selectedMode = AutoSwitchMode.ILLUMINANCE_AUTO, entitlementManager = themesManager)

    composeTestRule
      .onNode(hasText("Switch to dark below", substring = true), useUnmergedTree = true)
      .assertExists()
  }
}
