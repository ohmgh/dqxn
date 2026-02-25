package app.dqxn.android.feature.settings.privacy

import app.dqxn.android.data.device.ConnectionEventStore
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.feature.settings.main.MainSettingsViewModel
import app.dqxn.android.sdk.analytics.AnalyticsEvent
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests verifying PDPA consent flow (NF-P3): events are suppressed before consent, enabled after
 * consent, and consent toggle correctly gates tracking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsConsentFlowTest {

  private val testDispatcher = StandardTestDispatcher()
  private val analyticsConsentFlow = MutableStateFlow(false)

  private val analyticsTracker = mockk<AnalyticsTracker>(relaxed = true)

  private val userPreferencesRepository =
    mockk<UserPreferencesRepository>(relaxed = true) {
      every { analyticsConsent } returns analyticsConsentFlow
      every { showStatusBar } returns flowOf(false)
      every { orientationLock } returns flowOf("auto")
      every { keepScreenOn } returns flowOf(true)
    }

  private val providerSettingsStore = mockk<ProviderSettingsStore>(relaxed = true)
  private val layoutRepository = mockk<LayoutRepository>(relaxed = true)
  private val pairedDeviceStore = mockk<PairedDeviceStore>(relaxed = true)
  private val widgetStyleStore = mockk<WidgetStyleStore>(relaxed = true)
  private val connectionEventStore = mockk<ConnectionEventStore>(relaxed = true)

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): MainSettingsViewModel =
    MainSettingsViewModel(
      userPreferencesRepository = userPreferencesRepository,
      providerSettingsStore = providerSettingsStore,
      layoutRepository = layoutRepository,
      pairedDeviceStore = pairedDeviceStore,
      widgetStyleStore = widgetStyleStore,
      connectionEventStore = connectionEventStore,
      analyticsTracker = analyticsTracker,
    )

  @Test
  fun `events suppressed before consent -- tracker never enabled`() = runTest(testDispatcher) {
    // Consent is false (default)
    analyticsConsentFlow.value = false
    val viewModel = createViewModel()
    testScheduler.advanceUntilIdle()

    // Analytics consent is false -- tracker.setEnabled(true) should not have been called
    assertThat(viewModel.analyticsConsent.value).isFalse()
    verify(exactly = 0) { analyticsTracker.setEnabled(true) }
  }

  @Test
  fun `events fire after consent granted`() = runTest(testDispatcher) {
    analyticsConsentFlow.value = false
    val viewModel = createViewModel()

    // Grant consent
    viewModel.setAnalyticsConsent(true)
    testScheduler.advanceUntilIdle()

    verify { analyticsTracker.setEnabled(true) }
  }

  @Test
  fun `consent toggle stops events`() = runTest(testDispatcher) {
    // Start with consent granted
    analyticsConsentFlow.value = true
    val viewModel = createViewModel()
    testScheduler.advanceUntilIdle()

    // Enable analytics
    viewModel.setAnalyticsConsent(true)
    testScheduler.advanceUntilIdle()
    verify { analyticsTracker.setEnabled(true) }

    // Now revoke consent
    viewModel.setAnalyticsConsent(false)
    testScheduler.advanceUntilIdle()

    verify { analyticsTracker.setEnabled(false) }
  }

  @Test
  fun `consent default is false -- opt-in model`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    assertThat(viewModel.analyticsConsent.value).isFalse()
  }

  @Test
  fun `setAnalyticsConsent true persists preference before enabling tracker`() =
    runTest(testDispatcher) {
      val viewModel = createViewModel()

      viewModel.setAnalyticsConsent(true)
      testScheduler.advanceUntilIdle()

      io.mockk.coVerifyOrder {
        userPreferencesRepository.setAnalyticsConsent(true)
        analyticsTracker.setEnabled(true)
      }
    }

  @Test
  fun `setAnalyticsConsent false disables tracker before persisting preference`() =
    runTest(testDispatcher) {
      val viewModel = createViewModel()

      viewModel.setAnalyticsConsent(false)
      testScheduler.advanceUntilIdle()

      io.mockk.coVerifyOrder {
        analyticsTracker.setEnabled(false)
        userPreferencesRepository.setAnalyticsConsent(false)
      }
    }
}
