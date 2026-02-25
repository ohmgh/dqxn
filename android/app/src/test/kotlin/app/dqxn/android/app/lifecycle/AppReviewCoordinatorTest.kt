package app.dqxn.android.app.lifecycle

import android.app.Activity
import app.dqxn.android.data.preferences.UserPreferencesRepository
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppReviewCoordinator].
 *
 * Uses MockK for ReviewManager since FakeReviewManager requires real Android Context. Gating
 * conditions tested via injectable timeProvider and MutableStateFlow-backed UserPreferencesRepository
 * mock.
 */
@DisplayName("AppReviewCoordinator")
class AppReviewCoordinatorTest {

  private val reviewManager = mockk<ReviewManager>(relaxed = true)
  private val prefs = mockk<UserPreferencesRepository>(relaxed = true)
  private val activity = mockk<Activity>(relaxed = true)

  private val sessionCountFlow = MutableStateFlow(0)
  private val lastReviewTimestampFlow = MutableStateFlow(0L)
  private var currentTime = 1_000_000_000L

  private lateinit var coordinator: AppReviewCoordinator

  @BeforeEach
  fun setup() {
    every { prefs.sessionCount } returns sessionCountFlow
    every { prefs.lastReviewPromptTimestamp } returns lastReviewTimestampFlow

    // Mock the review flow chain: requestReviewFlow -> ReviewInfo -> launchReviewFlow
    val reviewInfo = mockk<ReviewInfo>()
    val requestTask = mockk<Task<ReviewInfo>>(relaxed = true)
    val launchTask = mockk<Task<Void>>(relaxed = true)

    every { reviewManager.requestReviewFlow() } returns requestTask
    every { requestTask.isComplete } returns true
    every { requestTask.isSuccessful } returns true
    every { requestTask.isCanceled } returns false
    every { requestTask.exception } returns null
    every { requestTask.result } returns reviewInfo
    every { reviewManager.launchReviewFlow(any(), any()) } returns launchTask
    every { launchTask.isComplete } returns true
    every { launchTask.isSuccessful } returns true
    every { launchTask.isCanceled } returns false
    every { launchTask.exception } returns null
    every { launchTask.result } returns null

    coordinator =
      AppReviewCoordinator(
        reviewManager = reviewManager,
        prefs = prefs,
        timeProvider = { currentTime },
      )
  }

  @Test
  @DisplayName("triggers review when all conditions met (3+ sessions, customized, no crash, first time)")
  fun `review triggered when all conditions met first time`() = runTest {
    sessionCountFlow.value = 3
    lastReviewTimestampFlow.value = 0L // Never prompted before

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    verify { reviewManager.requestReviewFlow() }
    verify { reviewManager.launchReviewFlow(activity, any()) }
    coVerify { prefs.setLastReviewPromptTimestamp(currentTime) }
  }

  @Test
  @DisplayName("triggers review when 90+ days since last prompt")
  fun `review triggered after 90 day gap`() = runTest {
    sessionCountFlow.value = 5
    val ninetyOneDaysAgo = currentTime - (91L * 24 * 3600 * 1000)
    lastReviewTimestampFlow.value = ninetyOneDaysAgo

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    verify { reviewManager.requestReviewFlow() }
  }

  @Test
  @DisplayName("does NOT trigger review when session count < 3")
  fun `review not triggered with insufficient sessions`() = runTest {
    sessionCountFlow.value = 2
    lastReviewTimestampFlow.value = 0L

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    verify(exactly = 0) { reviewManager.requestReviewFlow() }
  }

  @Test
  @DisplayName("does NOT trigger review when layout not customized")
  fun `review not triggered without customization`() = runTest {
    sessionCountFlow.value = 5
    lastReviewTimestampFlow.value = 0L

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = false,
      hasCrashedThisSession = false,
    )

    verify(exactly = 0) { reviewManager.requestReviewFlow() }
  }

  @Test
  @DisplayName("does NOT trigger review when crash occurred this session")
  fun `review not triggered after crash`() = runTest {
    sessionCountFlow.value = 5
    lastReviewTimestampFlow.value = 0L

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = true,
    )

    verify(exactly = 0) { reviewManager.requestReviewFlow() }
  }

  @Test
  @DisplayName("does NOT trigger review when last review within 90 days")
  fun `review not triggered within 90 day frequency cap`() = runTest {
    sessionCountFlow.value = 5
    val thirtyDaysAgo = currentTime - (30L * 24 * 3600 * 1000)
    lastReviewTimestampFlow.value = thirtyDaysAgo

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    verify(exactly = 0) { reviewManager.requestReviewFlow() }
  }

  @Test
  @DisplayName("does NOT trigger review when exactly at 90 day boundary")
  fun `review not triggered at exactly 90 days`() = runTest {
    sessionCountFlow.value = 5
    val exactlyNinetyDaysAgo = currentTime - (90L * 24 * 3600 * 1000)
    lastReviewTimestampFlow.value = exactlyNinetyDaysAgo

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    // Exactly 90 days is NOT > 90 days, so should not trigger
    verify(exactly = 0) { reviewManager.requestReviewFlow() }
  }

  @Test
  @DisplayName("updates lastReviewPromptTimestamp after successful review")
  fun `timestamp updated after review`() = runTest {
    sessionCountFlow.value = 3
    lastReviewTimestampFlow.value = 0L

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    coVerify { prefs.setLastReviewPromptTimestamp(currentTime) }
  }

  @Test
  @DisplayName("session count of exactly 3 is sufficient")
  fun `exactly three sessions is sufficient`() = runTest {
    sessionCountFlow.value = 3
    lastReviewTimestampFlow.value = 0L

    coordinator.maybeRequestReview(
      activity = activity,
      hasCustomizedLayout = true,
      hasCrashedThisSession = false,
    )

    verify { reviewManager.requestReviewFlow() }
  }
}
