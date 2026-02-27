package app.dqxn.android.feature.dashboard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.dqxn.android.sdk.contracts.status.WidgetRenderState
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose tests for [WidgetStatusOverlay] themed per-type differentiation.
 *
 * Tests verify:
 * - SetupRequired overlay renders and is tappable
 * - EntitlementRevoked overlay renders and is tappable
 * - Disconnected overlay renders in corner position
 * - ConnectionError overlay renders centered
 * - ProviderMissing overlay renders
 * - DataStale overlay renders
 * - Ready state produces no overlay nodes
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetStatusOverlayTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testTheme =
    DashboardThemeDefinition(
      themeId = "test",
      displayName = "Test",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  @Test
  fun `setup required overlay is tappable`() {
    var tapped = false

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.SetupRequired(message = "Setup needed"),
          onSetupTap = { tapped = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("status_setup_required").assertExists()
    composeTestRule.onNodeWithTag("status_setup_required").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_setup_required").performClick()
    assertThat(tapped).isTrue()
  }

  @Test
  fun `entitlement revoked overlay is tappable`() {
    var tapped = false

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.EntitlementRevoked(upgradeEntitlement = "plus"),
          onEntitlementTap = { tapped = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("status_entitlement_revoked").assertExists()
    composeTestRule.onNodeWithTag("status_entitlement_revoked").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_entitlement_revoked").performClick()
    assertThat(tapped).isTrue()
  }

  @Test
  fun `disconnected overlay renders in corner position`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.Disconnected,
        )
      }
    }

    composeTestRule.onNodeWithTag("status_disconnected").assertExists()
    composeTestRule.onNodeWithTag("status_disconnected").assertIsDisplayed()
  }

  @Test
  fun `connection error overlay renders centered`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.ConnectionError(message = "Failed"),
        )
      }
    }

    composeTestRule.onNodeWithTag("status_connection_error").assertExists()
    composeTestRule.onNodeWithTag("status_connection_error").assertIsDisplayed()
  }

  @Test
  fun `provider missing overlay renders`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.ProviderMissing,
        )
      }
    }

    composeTestRule.onNodeWithTag("status_provider_missing").assertExists()
    composeTestRule.onNodeWithTag("status_provider_missing").assertIsDisplayed()
  }

  @Test
  fun `data stale overlay renders`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.DataStale,
        )
      }
    }

    composeTestRule.onNodeWithTag("status_data_stale").assertExists()
    composeTestRule.onNodeWithTag("status_data_stale").assertIsDisplayed()
  }

  @Test
  fun `ready state renders nothing`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetStatusOverlay(
          renderState = WidgetRenderState.Ready,
        )
      }
    }

    // None of the status tags should exist
    composeTestRule.onNodeWithTag("status_setup_required").assertDoesNotExist()
    composeTestRule.onNodeWithTag("status_disconnected").assertDoesNotExist()
    composeTestRule.onNodeWithTag("status_connection_error").assertDoesNotExist()
    composeTestRule.onNodeWithTag("status_entitlement_revoked").assertDoesNotExist()
    composeTestRule.onNodeWithTag("status_provider_missing").assertDoesNotExist()
    composeTestRule.onNodeWithTag("status_data_stale").assertDoesNotExist()
  }
}
