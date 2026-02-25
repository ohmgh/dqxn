package app.dqxn.android.feature.settings.setup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.contracts.setup.SetupResult
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetupSheetTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

  private val freeEntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = true
      override fun getActiveEntitlements(): Set<String> = setOf("free")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  private fun createMockSettingsStore(): ProviderSettingsStore {
    val store = mockk<ProviderSettingsStore>(relaxed = true)
    every { store.getAllSettings(any(), any()) } returns
      flowOf(persistentMapOf<String, Any?>())
    return store
  }

  private fun createMockPairedDeviceStore(): PairedDeviceStore {
    val store = mockk<PairedDeviceStore>(relaxed = true)
    every { store.devices } returns MutableStateFlow(persistentListOf())
    return store
  }

  /**
   * Creates a mock [SetupEvaluatorImpl] that returns the given definitions as satisfied.
   * All other definitions are returned as unsatisfied.
   */
  private fun createMockEvaluator(
    satisfiedIds: Set<String> = emptySet(),
  ): SetupEvaluatorImpl {
    val evaluator = mockk<SetupEvaluatorImpl>(relaxed = true)
    every { evaluator.evaluateWithPersistence(any(), any()) } answers {
      val schema = firstArg<List<SetupPageDefinition>>()
      schema.flatMap { page ->
        page.definitions.map { definition ->
          SetupResult(
            definitionId = definition.id,
            satisfied = definition.id in satisfiedIds,
          )
        }
      }
    }
    return evaluator
  }

  // --- Test schemas ---

  /** Two-page schema: page 1 has an info item, page 2 has an info item. */
  private val twoPageSchema = listOf(
    SetupPageDefinition(
      id = "page1",
      title = "Page One",
      definitions = listOf(
        SetupDefinition.Info(
          id = "info1",
          label = "Page 1 Info",
          description = "First page information",
        ),
      ),
    ),
    SetupPageDefinition(
      id = "page2",
      title = "Page Two",
      definitions = listOf(
        SetupDefinition.Info(
          id = "info2",
          label = "Page 2 Info",
          description = "Second page information",
        ),
      ),
    ),
  )

  /** Two-page schema where page 1 has an unsatisfied permission requirement. */
  private val gatedPageSchema = listOf(
    SetupPageDefinition(
      id = "page_gated",
      title = "Gated Page",
      definitions = listOf(
        SetupDefinition.RuntimePermission(
          id = "perm_location",
          label = "Location Permission",
          description = "Required for GPS",
          permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
        ),
      ),
    ),
    SetupPageDefinition(
      id = "page_after",
      title = "After Gated",
      definitions = listOf(
        SetupDefinition.Info(
          id = "info_after",
          label = "After gated content",
        ),
      ),
    ),
  )

  /** Two-page schema: page 1 has only Setting items (always satisfied). */
  private val settingOnlySchema = listOf(
    SetupPageDefinition(
      id = "page_settings",
      title = "Settings",
      definitions = listOf(
        SetupDefinition.Setting(
          definition = app.dqxn.android.sdk.contracts.settings.SettingDefinition.BooleanSetting(
            key = "test_bool",
            label = "Test Toggle",
            default = false,
          ),
        ),
      ),
    ),
    SetupPageDefinition(
      id = "page_done",
      title = "Done",
      definitions = listOf(
        SetupDefinition.Info(
          id = "info_done",
          label = "Setup complete",
        ),
      ),
    ),
  )

  // --- Tests ---

  @Test
  fun `multi-page navigation -- renders page 1 then navigates to page 2`() {
    val evaluator = createMockEvaluator(satisfiedIds = setOf("info1", "info2"))
    renderSetupSheet(schema = twoPageSchema, evaluator = evaluator)

    // Page 1 visible
    composeTestRule.onNodeWithText("Page 1 Info").assertIsDisplayed()

    // Tap Next
    composeTestRule.onNodeWithTag("setup_next_button").performClick()
    composeTestRule.waitForIdle()

    // Page 2 visible
    composeTestRule.onNodeWithText("Page 2 Info").assertIsDisplayed()
  }

  @Test
  fun `forward gating -- unsatisfied permission dims next button`() {
    // perm_location NOT in satisfiedIds -> page is gated
    val evaluator = createMockEvaluator(satisfiedIds = emptySet())
    renderSetupSheet(schema = gatedPageSchema, evaluator = evaluator)

    // Next button should exist and be tappable (alpha-dimmed, NOT disabled per Pitfall 6)
    composeTestRule.onNodeWithTag("setup_next_button").assertExists()
    composeTestRule.onNodeWithTag("setup_next_button").performClick()
  }

  @Test
  fun `back navigation -- navigate to page 2 then back to page 1`() {
    val evaluator = createMockEvaluator(satisfiedIds = setOf("info1", "info2"))
    renderSetupSheet(schema = twoPageSchema, evaluator = evaluator)

    // Navigate to page 2
    composeTestRule.onNodeWithTag("setup_next_button").performClick()
    composeTestRule.waitForIdle()

    // Verify on page 2
    composeTestRule.onNodeWithText("Page 2 Info").assertIsDisplayed()

    // Tap back button
    composeTestRule.onNodeWithTag("setup_back_button").performClick()
    composeTestRule.waitForIdle()

    // Back on page 1
    composeTestRule.onNodeWithText("Page 1 Info").assertIsDisplayed()
  }

  @Test
  fun `completion -- satisfied final page Done tap fires onComplete`() {
    var completeCalled = false
    val evaluator = createMockEvaluator(satisfiedIds = setOf("info1", "info2"))
    renderSetupSheet(
      schema = twoPageSchema,
      evaluator = evaluator,
      onComplete = { completeCalled = true },
    )

    // Navigate to page 2
    composeTestRule.onNodeWithTag("setup_next_button").performClick()
    composeTestRule.waitForIdle()

    // Tap Done
    composeTestRule.onNodeWithTag("setup_done_button").performClick()
    composeTestRule.waitForIdle()

    assertThat(completeCalled).isTrue()
  }

  @Test
  fun `dismissal -- on page 0 back press fires onDismiss`() {
    var dismissCalled = false
    val evaluator = createMockEvaluator(satisfiedIds = setOf("info1", "info2"))
    renderSetupSheet(
      schema = twoPageSchema,
      evaluator = evaluator,
      onDismiss = { dismissCalled = true },
    )

    // Trigger back press via the Activity's onBackPressedDispatcher
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()

    assertThat(dismissCalled).isTrue()
  }

  @Test
  fun `setting-only page is always ungated`() {
    // Setting types always satisfy -- evaluator returns them as satisfied,
    // and even if it didn't, the gating logic only blocks on isRequirement types
    val evaluator = createMockEvaluator(satisfiedIds = setOf("test_bool", "info_done"))
    renderSetupSheet(schema = settingOnlySchema, evaluator = evaluator)

    // Page with Setting items should show Next button and navigation should work
    composeTestRule.onNodeWithTag("setup_next_button").assertExists()
    composeTestRule.onNodeWithTag("setup_next_button").performClick()
    composeTestRule.waitForIdle()

    // Should navigate to page 2 successfully
    composeTestRule.onNodeWithText("Setup complete").assertIsDisplayed()
  }

  // --- Helper ---

  private fun renderSetupSheet(
    schema: List<SetupPageDefinition>,
    evaluator: SetupEvaluatorImpl,
    onComplete: () -> Unit = {},
    onDismiss: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        SetupSheet(
          setupSchema = schema,
          packId = "test-pack",
          providerId = "test-provider",
          providerSettingsStore = createMockSettingsStore(),
          pairedDeviceStore = createMockPairedDeviceStore(),
          evaluator = evaluator,
          entitlementManager = freeEntitlementManager,
          onComplete = onComplete,
          onDismiss = onDismiss,
        )
      }
    }
    composeTestRule.waitForIdle()
  }
}
