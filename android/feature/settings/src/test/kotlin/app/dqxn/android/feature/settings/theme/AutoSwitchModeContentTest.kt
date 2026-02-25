package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
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

  /** No premium entitlement -- lock icons should appear. */
  private val noPremiumManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = id != "plus"
      override fun getActiveEntitlements(): Set<String> = setOf("free")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  /** Has premium entitlement -- no lock icons. */
  private val premiumManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = true
      override fun getActiveEntitlements(): Set<String> = setOf("free", "plus")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  @Test
  fun `SOLAR_AUTO shows lock icon when ungated`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        AutoSwitchModeContent(
          selectedMode = AutoSwitchMode.SYSTEM,
          illuminanceThreshold = 100f,
          entitlementManager = noPremiumManager,
          onModeSelected = {},
          onIlluminanceThresholdChanged = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("lock_icon_SOLAR_AUTO", useUnmergedTree = true)
      .assertExists()
      .assertIsDisplayed()
  }

  @Test
  fun `ILLUMINANCE_AUTO shows lock icon when ungated`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        AutoSwitchModeContent(
          selectedMode = AutoSwitchMode.SYSTEM,
          illuminanceThreshold = 100f,
          entitlementManager = noPremiumManager,
          onModeSelected = {},
          onIlluminanceThresholdChanged = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("lock_icon_ILLUMINANCE_AUTO", useUnmergedTree = true)
      .assertExists()
      .assertIsDisplayed()
  }

  @Test
  fun `lock icons hidden when entitled`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        AutoSwitchModeContent(
          selectedMode = AutoSwitchMode.SYSTEM,
          illuminanceThreshold = 100f,
          entitlementManager = premiumManager,
          onModeSelected = {},
          onIlluminanceThresholdChanged = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("lock_icon_SOLAR_AUTO", useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule
      .onNodeWithTag("lock_icon_ILLUMINANCE_AUTO", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun `illuminance control shown when ILLUMINANCE_AUTO selected`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        AutoSwitchModeContent(
          selectedMode = AutoSwitchMode.ILLUMINANCE_AUTO,
          illuminanceThreshold = 100f,
          entitlementManager = premiumManager,
          onModeSelected = {},
          onIlluminanceThresholdChanged = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("illuminance_control", useUnmergedTree = true)
      .assertExists()
      .assertIsDisplayed()
  }

  @Test
  fun `illuminance control hidden for other modes`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        AutoSwitchModeContent(
          selectedMode = AutoSwitchMode.SYSTEM,
          illuminanceThreshold = 100f,
          entitlementManager = premiumManager,
          onModeSelected = {},
          onIlluminanceThresholdChanged = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("illuminance_control", useUnmergedTree = true)
      .assertDoesNotExist()
  }
}
