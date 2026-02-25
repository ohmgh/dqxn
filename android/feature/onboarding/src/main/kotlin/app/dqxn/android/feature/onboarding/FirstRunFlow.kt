package app.dqxn.android.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import kotlinx.collections.immutable.ImmutableList

/**
 * Paginated first-run onboarding flow (F11.1, F11.2).
 *
 * 4 steps via [AnimatedContent] with horizontal slide transitions:
 * 1. [AnalyticsConsentStep] -- advance regardless of choice
 * 2. [FirstLaunchDisclaimer] -- dismiss to advance
 * 3. Theme selection -- simplified grid of free themes only
 * 4. Edit mode tour -- explainer cards, "Done" completes onboarding
 *
 * NO permission requests in this flow (F11.6 -- lazy).
 */
@Composable
public fun FirstRunFlow(
  freeThemes: ImmutableList<DashboardThemeDefinition>,
  onConsent: (Boolean) -> Unit,
  onDismissDisclaimer: () -> Unit,
  onSelectTheme: (String) -> Unit,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var currentPage by rememberSaveable { mutableIntStateOf(0) }
  var selectedThemeId by rememberSaveable { mutableStateOf<String?>(null) }

  // Track animation direction for horizontal slide
  var previousPage by rememberSaveable { mutableIntStateOf(0) }
  val isForward = currentPage >= previousPage

  BackHandler(enabled = currentPage > 0) { currentPage-- }

  Column(modifier = modifier.fillMaxSize().testTag("first_run_flow")) {
    AnimatedContent(
      targetState = currentPage,
      modifier = Modifier.weight(1f),
      transitionSpec = {
        if (isForward) {
          slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
        } else {
          slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
        }
      },
      label = "onboarding_page",
    ) { page ->
      when (page) {
        0 ->
          AnalyticsConsentStep(
            onConsent = { enabled ->
              onConsent(enabled)
              previousPage = 0
              currentPage = 1
            },
            modifier = Modifier.fillMaxSize(),
          )
        1 ->
          FirstLaunchDisclaimer(
            onDismiss = {
              onDismissDisclaimer()
              previousPage = 1
              currentPage = 2
            },
            modifier = Modifier.fillMaxSize(),
          )
        2 ->
          ThemeSelectionStep(
            freeThemes = freeThemes,
            selectedThemeId = selectedThemeId,
            onSelectTheme = { themeId ->
              selectedThemeId = themeId
              onSelectTheme(themeId)
            },
            onContinue = {
              previousPage = 2
              currentPage = 3
            },
            modifier = Modifier.fillMaxSize(),
          )
        3 ->
          EditModeTourStep(
            onDone = onComplete,
            modifier = Modifier.fillMaxSize(),
          )
      }
    }

    // Page indicator dots
    PageIndicator(
      pageCount = TOTAL_PAGES,
      currentPage = currentPage,
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
    )
  }
}

/** Free theme selection grid (F11.2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSelectionStep(
  freeThemes: ImmutableList<DashboardThemeDefinition>,
  selectedThemeId: String?,
  onSelectTheme: (String) -> Unit,
  onContinue: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.testTag("theme_selection_step").padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.onboarding_theme_title),
      style = MaterialTheme.typography.headlineMedium,
    )

    Spacer(modifier = Modifier.height(24.dp))

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      freeThemes.forEach { theme ->
        ThemeCard(
          theme = theme,
          isSelected = theme.themeId == selectedThemeId,
          onClick = { onSelectTheme(theme.themeId) },
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = onContinue,
      modifier = Modifier.fillMaxWidth().testTag("theme_continue"),
    ) {
      Text(text = stringResource(R.string.onboarding_theme_continue))
    }
  }
}

/** Theme preview card for selection. */
@Composable
private fun ThemeCard(
  theme: DashboardThemeDefinition,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .testTag("theme_card_${theme.themeId}")
        .clickable(onClick = onClick),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isSelected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceVariant,
      ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = theme.displayName,
        style = MaterialTheme.typography.titleSmall,
      )
      Text(
        text = if (theme.isDark) "Dark" else "Light",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Edit mode tour step with explainer cards. */
@Composable
private fun EditModeTourStep(
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.testTag("edit_mode_tour_step").padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.onboarding_tour_title),
      style = MaterialTheme.typography.headlineMedium,
    )

    Spacer(modifier = Modifier.height(24.dp))

    TourCard(text = stringResource(R.string.onboarding_tour_edit))
    Spacer(modifier = Modifier.height(12.dp))
    TourCard(text = stringResource(R.string.onboarding_tour_move))
    Spacer(modifier = Modifier.height(12.dp))
    TourCard(text = stringResource(R.string.onboarding_tour_resize))

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = onDone,
      modifier = Modifier.fillMaxWidth().testTag("tour_done"),
    ) {
      Text(text = stringResource(R.string.onboarding_tour_done))
    }
  }
}

/** Simple explainer card for the tour step. */
@Composable
private fun TourCard(
  text: String,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(16.dp),
    )
  }
}

/** Horizontal page indicator dots. */
@Composable
private fun PageIndicator(
  pageCount: Int,
  currentPage: Int,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.testTag("page_indicator"),
    horizontalArrangement = Arrangement.Center,
  ) {
    repeat(pageCount) { index ->
      val color =
        if (index == currentPage) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
      Box(
        modifier =
          Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape).background(color),
      )
    }
  }
}

private const val TOTAL_PAGES: Int = 4
