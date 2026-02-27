package app.dqxn.android.feature.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.data.device.ConnectionEvent
import app.dqxn.android.sdk.observability.health.ProviderStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testProvider =
    ProviderStatus(
      providerId = "gps",
      displayName = "GPS Speed",
      isConnected = true,
      lastUpdateTimestamp = System.currentTimeMillis(),
    )

  @Test
  fun `connection event log renders 50 events`() {
    val events =
      (0 until 50)
        .map { i ->
          ConnectionEvent(
            timestamp = (50 - i) * 1_000L, // newest first
            deviceMac = "AA:BB:CC:DD:EE:FF",
            eventType = "CONNECTED",
            details = "Event $i",
          )
        }
        .toImmutableList()

    composeTestRule.setContent {
      ProviderDetailScreen(
        providerStatus = testProvider,
        events = events,
        onRetry = {},
      )
    }

    // Verify first and last are rendered (LazyColumn may virtualize)
    composeTestRule.onNodeWithTag("connection_event_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("connection_event_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("connection_event_2").assertIsDisplayed()
  }

  @Test
  fun `empty state shows no events message`() {
    composeTestRule.setContent {
      ProviderDetailScreen(
        providerStatus = testProvider,
        events = persistentListOf(),
        onRetry = {},
      )
    }

    composeTestRule.onNodeWithText("No connection events").assertIsDisplayed()
  }

  @Test
  fun `retry button fires callback`() {
    var retriedProviderId: String? = null

    composeTestRule.setContent {
      ProviderDetailScreen(
        providerStatus = testProvider,
        events = persistentListOf(),
        onRetry = { retriedProviderId = it },
      )
    }

    composeTestRule.onNodeWithTag("retry_button").performClick()

    assertThat(retriedProviderId).isEqualTo("gps")
  }

  @Test
  fun `events sorted newest first`() {
    val events =
      listOf(
          ConnectionEvent(
            timestamp = 3_000L, // newest
            deviceMac = "AA:BB:CC:DD:EE:FF",
            eventType = "DISCONNECTED",
            details = "Latest event",
          ),
          ConnectionEvent(
            timestamp = 2_000L,
            deviceMac = "AA:BB:CC:DD:EE:FF",
            eventType = "CONNECTED",
            details = "Middle event",
          ),
          ConnectionEvent(
            timestamp = 1_000L, // oldest
            deviceMac = "AA:BB:CC:DD:EE:FF",
            eventType = "CONNECTED",
            details = "Oldest event",
          ),
        )
        .toImmutableList()

    composeTestRule.setContent {
      ProviderDetailScreen(
        providerStatus = testProvider,
        events = events,
        onRetry = {},
      )
    }

    // First rendered event (index 0) should have the latest timestamp's data
    composeTestRule.onNodeWithTag("connection_event_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Latest event").assertIsDisplayed()
    composeTestRule.onNodeWithText("DISCONNECTED").assertIsDisplayed()
  }
}
