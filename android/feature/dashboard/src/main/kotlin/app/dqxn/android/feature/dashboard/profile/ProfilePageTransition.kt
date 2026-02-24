package app.dqxn.android.feature.dashboard.profile

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.dqxn.android.feature.dashboard.coordinator.ProfileInfo
import kotlinx.collections.immutable.ImmutableList

/**
 * Profile switching via [HorizontalPager] from Compose Foundation.
 *
 * When a single profile exists, renders content directly without pager overhead. When two or more
 * profiles exist, wraps content in [HorizontalPager] for horizontal swipe switching.
 *
 * Profile switching is disabled during edit mode (F1.29): horizontal swipe in edit mode is widget
 * drag territory, not profile switching. The pager's `userScrollEnabled` is set to false when
 * [isEditMode] is true.
 *
 * @param profiles All available profiles.
 * @param activeProfileId Currently active profile ID.
 * @param isEditMode Whether edit mode is active (disables swiping).
 * @param onSwitchProfile Callback when user swipes to a different profile.
 * @param content Composable content for each profile, receiving the profile ID.
 */
@Composable
public fun ProfilePageTransition(
  profiles: ImmutableList<ProfileInfo>,
  activeProfileId: String,
  isEditMode: Boolean,
  onSwitchProfile: (String) -> Unit,
  content: @Composable (profileId: String) -> Unit,
) {
  if (profiles.size <= 1) {
    // Single or no profile -- render directly without pager
    content(activeProfileId)
    return
  }

  val activeIndex = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)

  val pagerState = rememberPagerState(
    initialPage = activeIndex,
    pageCount = { profiles.size },
  )

  // Sync pager to external profile changes (e.g., bottom bar tap)
  LaunchedEffect(activeIndex) {
    if (pagerState.currentPage != activeIndex) {
      pagerState.animateScrollToPage(activeIndex)
    }
  }

  // Notify when user swipes to a different profile
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
      .collect { page ->
        val profile = profiles.getOrNull(page)
        if (profile != null && profile.id != activeProfileId) {
          onSwitchProfile(profile.id)
        }
      }
  }

  HorizontalPager(
    state = pagerState,
    userScrollEnabled = !isEditMode,
  ) { page ->
    val profile = profiles.getOrNull(page)
    if (profile != null) {
      content(profile.id)
    }
  }
}
