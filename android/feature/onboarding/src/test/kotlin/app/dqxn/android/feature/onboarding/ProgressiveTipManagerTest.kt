package app.dqxn.android.feature.onboarding

import app.cash.turbine.test
import app.dqxn.android.data.preferences.UserPreferencesRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ProgressiveTipManagerTest {

  private val repo: UserPreferencesRepository = mockk(relaxed = true)
  private lateinit var tipManager: ProgressiveTipManager

  @BeforeEach
  fun setup() {
    tipManager = ProgressiveTipManager(repo)
  }

  @Test
  fun `shouldShowTip returns true for unseen tip`() = runTest {
    every { repo.hasSeenTip("first_launch") } returns flowOf(false)

    tipManager.shouldShowTip("first_launch").test {
      assertThat(awaitItem()).isTrue()
      awaitComplete()
    }
  }

  @Test
  fun `shouldShowTip returns false for seen tip`() = runTest {
    every { repo.hasSeenTip("edit_mode") } returns flowOf(true)

    tipManager.shouldShowTip("edit_mode").test {
      assertThat(awaitItem()).isFalse()
      awaitComplete()
    }
  }

  @Test
  fun `dismissTip calls markTipSeen`() = runTest {
    tipManager.dismissTip("widget_focus")

    coVerify(exactly = 1) { repo.markTipSeen("widget_focus") }
  }

  @Test
  fun `all 4 tip keys are distinct constants`() {
    val keys =
      setOf(
        ProgressiveTipManager.TIP_FIRST_LAUNCH,
        ProgressiveTipManager.TIP_EDIT_MODE,
        ProgressiveTipManager.TIP_WIDGET_FOCUS,
        ProgressiveTipManager.TIP_WIDGET_SETTINGS,
      )
    assertThat(keys).hasSize(4)
    assertThat(ProgressiveTipManager.TIP_FIRST_LAUNCH).isEqualTo("first_launch")
    assertThat(ProgressiveTipManager.TIP_EDIT_MODE).isEqualTo("edit_mode")
    assertThat(ProgressiveTipManager.TIP_WIDGET_FOCUS).isEqualTo("widget_focus")
    assertThat(ProgressiveTipManager.TIP_WIDGET_SETTINGS).isEqualTo("widget_settings")
  }

  @Test
  fun `shouldShowTip reacts to preference changes`() = runTest {
    val seenFlow = MutableStateFlow(false)
    every { repo.hasSeenTip("widget_settings") } returns seenFlow

    tipManager.shouldShowTip("widget_settings").test {
      // Initially unseen -> should show
      assertThat(awaitItem()).isTrue()

      // Mark as seen -> should not show
      seenFlow.value = true
      assertThat(awaitItem()).isFalse()

      cancelAndIgnoreRemainingEvents()
    }
  }
}
