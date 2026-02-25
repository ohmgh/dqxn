package app.dqxn.android.app.lifecycle

import android.app.Activity
import app.dqxn.android.data.preferences.UserPreferencesRepository
import com.google.android.play.core.review.ReviewManager
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Coordinates Google Play In-App Review prompt (NF-L3).
 *
 * Gating conditions (ALL must be true):
 * 1. Session count >= 3
 * 2. User has customized layout (passed as parameter)
 * 3. No crash in current session (passed as parameter)
 * 4. At least 90 days since last review prompt (or never prompted)
 *
 * Uses injectable [timeProvider] for testability.
 */
public class AppReviewCoordinator
@Inject
constructor(
  private val reviewManager: ReviewManager,
  private val prefs: UserPreferencesRepository,
  private val timeProvider: () -> Long = System::currentTimeMillis,
) {

  /**
   * Requests a review prompt if all gating conditions are met.
   *
   * @param activity The activity to display the review UI on.
   * @param hasCustomizedLayout Whether the user has customized the dashboard layout.
   * @param hasCrashedThisSession Whether a crash occurred during the current session.
   */
  public suspend fun maybeRequestReview(
    activity: Activity,
    hasCustomizedLayout: Boolean,
    hasCrashedThisSession: Boolean,
  ) {
    val sessionCount = prefs.sessionCount.first()
    val lastReviewTimestamp = prefs.lastReviewPromptTimestamp.first()
    val now = timeProvider()

    val meetsSessionRequirement = sessionCount >= MIN_SESSION_COUNT
    val meetsCustomizationRequirement = hasCustomizedLayout
    val meetsNoCrashRequirement = !hasCrashedThisSession
    val meetsFrequencyCap =
      lastReviewTimestamp == 0L || (now - lastReviewTimestamp) > NINETY_DAYS_MS

    if (
      meetsSessionRequirement &&
        meetsCustomizationRequirement &&
        meetsNoCrashRequirement &&
        meetsFrequencyCap
    ) {
      val reviewInfo = reviewManager.requestReviewFlow().await()
      reviewManager.launchReviewFlow(activity, reviewInfo).await()
      prefs.setLastReviewPromptTimestamp(now)
    }
  }

  private companion object {
    /** Minimum sessions before review prompt is eligible. */
    const val MIN_SESSION_COUNT = 3

    /** 90 days in milliseconds. */
    const val NINETY_DAYS_MS = 90L * 24 * 3600 * 1000
  }
}
