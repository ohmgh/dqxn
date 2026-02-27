package app.dqxn.android.feature.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.sdk.observability.health.ProviderStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderHealthDashboardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val now = 1_000_000L

  @Test
  fun `renders provider list with statuses`() {
    val statuses =
      listOf(
          providerStatus("gps", "GPS Speed", isConnected = true, lastUpdate = now - 2_000),
          providerStatus("accel", "Accelerometer", isConnected = true, lastUpdate = now - 5_000),
          providerStatus("battery", "Battery", isConnected = false, lastUpdate = now - 8_000),
        )
        .toImmutableList()

    composeTestRule.setContent {
      ProviderHealthDashboard(
        statuses = statuses,
        currentTimeMs = now,
        onProviderClick = {},
      )
    }

    composeTestRule.onNodeWithTag("provider_row_gps").assertIsDisplayed()
    composeTestRule.onNodeWithTag("provider_row_accel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("provider_row_battery").assertIsDisplayed()
    composeTestRule.onNodeWithText("GPS Speed").assertIsDisplayed()
    composeTestRule.onNodeWithText("Accelerometer").assertIsDisplayed()
    composeTestRule.onNodeWithText("Battery").assertIsDisplayed()
  }

  @Test
  fun `staleness indicator shown for stale provider`() {
    val statuses =
      persistentListOf(
        providerStatus(
          "stale-provider",
          "Stale Provider",
          isConnected = true,
          lastUpdate = now - 15_000, // 15s ago, exceeds 10s threshold
        ),
      )

    composeTestRule.setContent {
      ProviderHealthDashboard(
        statuses = statuses,
        currentTimeMs = now,
        onProviderClick = {},
      )
    }

    composeTestRule
      .onNodeWithTag("staleness_indicator_stale-provider", useUnmergedTree = true)
      .assertIsDisplayed()
  }

  @Test
  fun `connected provider shows green indicator`() {
    val statuses =
      persistentListOf(
        providerStatus("gps", "GPS Speed", isConnected = true, lastUpdate = now - 1_000),
      )

    composeTestRule.setContent {
      ProviderHealthDashboard(
        statuses = statuses,
        currentTimeMs = now,
        onProviderClick = {},
      )
    }

    composeTestRule
      .onNodeWithTag("connection_indicator_gps", useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("provider_row_gps", useUnmergedTree = true).assertIsDisplayed()
    // No staleness indicator for fresh provider
    composeTestRule
      .onNodeWithTag("staleness_indicator_gps", useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun `empty state shows message`() {
    composeTestRule.setContent {
      ProviderHealthDashboard(
        statuses = persistentListOf(),
        currentTimeMs = now,
        onProviderClick = {},
      )
    }

    composeTestRule.onNodeWithText("No providers registered").assertIsDisplayed()
  }

  @Test
  fun `tap navigates to detail`() {
    var clickedProviderId: String? = null
    val statuses =
      persistentListOf(
        providerStatus("gps", "GPS Speed", isConnected = true, lastUpdate = now - 1_000),
      )

    composeTestRule.setContent {
      ProviderHealthDashboard(
        statuses = statuses,
        currentTimeMs = now,
        onProviderClick = { clickedProviderId = it },
      )
    }

    composeTestRule.onNodeWithTag("provider_row_gps").performClick()

    assertThat(clickedProviderId).isEqualTo("gps")
  }

  private fun providerStatus(
    id: String,
    name: String,
    isConnected: Boolean,
    lastUpdate: Long,
    error: String? = null,
  ): ProviderStatus =
    ProviderStatus(
      providerId = id,
      displayName = name,
      isConnected = isConnected,
      lastUpdateTimestamp = lastUpdate,
      errorDescription = error,
    )
}
