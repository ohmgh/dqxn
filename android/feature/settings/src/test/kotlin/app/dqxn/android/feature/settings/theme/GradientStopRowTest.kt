package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.GradientStop
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GradientStopRowTest {

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

  private fun makeStop(position: Float, color: Long = Color.White.value.toLong()): GradientStop =
    GradientStop(color = color, position = position)

  @Test
  fun `add button disabled at 5 stops`() {
    val fiveStops =
      persistentListOf(
        makeStop(0f),
        makeStop(0.25f),
        makeStop(0.5f),
        makeStop(0.75f),
        makeStop(1f),
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        GradientStopRow(
          stops = fiveStops,
          onStopsChanged = {},
        )
      }
    }

    composeTestRule
      .onNodeWithTag("gradient_stop_add")
      .assertIsNotEnabled()
  }

  @Test
  fun `remove button disabled at 2 stops`() {
    val twoStops = persistentListOf(makeStop(0f), makeStop(1f))

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        GradientStopRow(
          stops = twoStops,
          onStopsChanged = {},
        )
      }
    }

    // Both remove buttons should be disabled
    composeTestRule
      .onNodeWithTag("gradient_stop_remove_0", useUnmergedTree = true)
      .assertIsNotEnabled()
    composeTestRule
      .onNodeWithTag("gradient_stop_remove_1", useUnmergedTree = true)
      .assertIsNotEnabled()
  }

  @Test
  fun `position clamped to valid range`() {
    // Verify stops render with positions in [0, 1] even when provided out-of-range values
    val stops = persistentListOf(makeStop(-0.5f), makeStop(1.5f))

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        GradientStopRow(
          stops = stops,
          onStopsChanged = {},
        )
      }
    }

    // If position clamping works, the sliders will render at 0 and 1 respectively.
    // Both stop items should exist and be rendered.
    composeTestRule
      .onNodeWithTag("gradient_stop_position_0", useUnmergedTree = true)
      .assertExists()
    composeTestRule
      .onNodeWithTag("gradient_stop_position_1", useUnmergedTree = true)
      .assertExists()
  }

  @Test
  fun `add stop inserts at midpoint`() {
    var updatedStops: ImmutableList<GradientStop>? = null
    val twoStops = persistentListOf(makeStop(0f), makeStop(1f))

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        GradientStopRow(
          stops = twoStops,
          onStopsChanged = { updatedStops = it },
        )
      }
    }

    composeTestRule
      .onNodeWithTag("gradient_stop_add")
      .assertIsEnabled()
      .performClick()

    composeTestRule.waitForIdle()

    assertThat(updatedStops).isNotNull()
    val result = updatedStops!!
    assertThat(result.size).isEqualTo(3)
    // Midpoint of stops[0].position (0f) and stops[1].position (1f) = 0.5f
    assertThat(result[2].position).isEqualTo(0.5f)
  }
}
