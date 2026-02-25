package app.dqxn.android.feature.settings.main

import app.dqxn.android.data.device.ConnectionEventStore
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainSettingsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val analyticsConsentFlow = MutableStateFlow(false)

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
  private val analyticsTracker = mockk<AnalyticsTracker>(relaxed = true)

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
  fun `analytics consent defaults to false`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    assertThat(viewModel.analyticsConsent.value).isFalse()
  }

  @Test
  fun `setAnalyticsConsent true enables tracker after persisting preference`() =
    runTest(testDispatcher) {
      val viewModel = createViewModel()

      viewModel.setAnalyticsConsent(true)
      testScheduler.advanceUntilIdle()

      coVerify { userPreferencesRepository.setAnalyticsConsent(true) }
      verify { analyticsTracker.setEnabled(true) }
    }

  @Test
  fun `setAnalyticsConsent false disables tracker before persisting preference`() =
    runTest(testDispatcher) {
      val viewModel = createViewModel()

      viewModel.setAnalyticsConsent(false)
      testScheduler.advanceUntilIdle()

      coVerifyOrder {
        analyticsTracker.setEnabled(false)
        userPreferencesRepository.setAnalyticsConsent(false)
      }
    }

  @Test
  fun `deleteAllData clears all 6 stores and disables analytics`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.deleteAllData()
    testScheduler.advanceUntilIdle()

    coVerify { userPreferencesRepository.clearAll() }
    coVerify { providerSettingsStore.clearAll() }
    coVerify { layoutRepository.clearAll() }
    coVerify { pairedDeviceStore.clearAll() }
    coVerify { widgetStyleStore.clearAll() }
    coVerify { connectionEventStore.clear() }
    verify { analyticsTracker.setEnabled(false) }
  }

  @Test
  fun `analyticsConsent stateIn is wired to repository flow`() = runTest(testDispatcher) {
    // Pre-set upstream to true before creating ViewModel
    analyticsConsentFlow.value = true

    val viewModel = createViewModel()

    // Collect to activate WhileSubscribed stateIn
    backgroundScope.launch(testDispatcher) { viewModel.analyticsConsent.collect {} }
    testScheduler.advanceUntilIdle()

    assertThat(viewModel.analyticsConsent.value).isTrue()
  }
}
