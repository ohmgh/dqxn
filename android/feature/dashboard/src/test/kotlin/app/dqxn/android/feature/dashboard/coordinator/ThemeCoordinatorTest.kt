package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.core.design.theme.BuiltInThemes
import app.dqxn.android.core.design.theme.ThemeAutoSwitchEngine
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.MinimalistTheme
import app.dqxn.android.sdk.ui.theme.SlateTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeCoordinatorTest {

  private val scheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(scheduler)
  private val testScope = CoroutineScope(testDispatcher)

  private val isDarkActiveFlow = MutableStateFlow(false)
  private val autoSwitchModeFlow = MutableStateFlow(AutoSwitchMode.SYSTEM)
  private val lightThemeIdFlow = MutableStateFlow("minimalist")
  private val darkThemeIdFlow = MutableStateFlow("slate")

  private val mockEngine = mockk<ThemeAutoSwitchEngine>(relaxed = true) {
    every { isDarkActive } returns isDarkActiveFlow
  }

  private val mockBuiltInThemes = mockk<BuiltInThemes> {
    every { minimalist } returns MinimalistTheme
    every { slate } returns SlateTheme
    every { resolveById("minimalist") } returns MinimalistTheme
    every { resolveById("slate") } returns SlateTheme
    every { resolveById("nonexistent") } returns null
  }

  private val mockPrefsRepo = mockk<UserPreferencesRepository>(relaxed = true) {
    every { autoSwitchMode } returns autoSwitchModeFlow
    every { lightThemeId } returns lightThemeIdFlow
    every { darkThemeId } returns darkThemeIdFlow
  }

  private lateinit var coordinator: ThemeCoordinator

  @BeforeEach
  fun setUp() {
    coordinator =
      ThemeCoordinator(
        themeAutoSwitchEngine = mockEngine,
        builtInThemes = mockBuiltInThemes,
        userPreferencesRepository = mockPrefsRepo,
        logger = NoOpLogger,
      )
  }

  @AfterEach
  fun tearDown() {
    testScope.cancel()
  }

  private fun advance() {
    scheduler.advanceUntilIdle()
  }

  @Nested
  @DisplayName("initialize")
  inner class Initialize {

    @Test
    fun `collects current theme from auto-switch engine`() {
      coordinator.initialize(testScope)
      advance()

      // isDarkActive is false -> currentTheme should be lightTheme (minimalist)
      assertThat(coordinator.themeState.value.currentTheme).isEqualTo(MinimalistTheme)
    }

    @Test
    fun `auto-switch engine dark mode change updates currentTheme`() {
      coordinator.initialize(testScope)
      advance()

      assertThat(coordinator.themeState.value.currentTheme).isEqualTo(MinimalistTheme)

      isDarkActiveFlow.value = true
      advance()

      assertThat(coordinator.themeState.value.currentTheme).isEqualTo(SlateTheme)
    }

    @Test
    fun `collects autoSwitchMode from preferences`() {
      coordinator.initialize(testScope)
      advance()

      assertThat(coordinator.themeState.value.autoSwitchMode).isEqualTo(AutoSwitchMode.SYSTEM)

      autoSwitchModeFlow.value = AutoSwitchMode.DARK
      advance()

      assertThat(coordinator.themeState.value.autoSwitchMode).isEqualTo(AutoSwitchMode.DARK)
    }
  }

  @Nested
  @DisplayName("handleSetTheme")
  inner class HandleSetTheme {

    @Test
    fun `updates currentTheme and persists to repository`() =
      runTest(testDispatcher) {
        coordinator.initialize(testScope)
        advance()

        coordinator.handleSetTheme("slate")

        assertThat(coordinator.themeState.value.currentTheme).isEqualTo(SlateTheme)
        coVerify { mockPrefsRepo.setDarkThemeId("slate") }
      }

    @Test
    fun `ignores unknown theme ID`() =
      runTest(testDispatcher) {
        coordinator.initialize(testScope)
        advance()

        val before = coordinator.themeState.value.currentTheme
        coordinator.handleSetTheme("nonexistent")

        assertThat(coordinator.themeState.value.currentTheme).isEqualTo(before)
      }
  }

  @Nested
  @DisplayName("handlePreviewTheme")
  inner class HandlePreviewTheme {

    @Test
    fun `sets preview theme, displayTheme returns preview`() {
      coordinator.initialize(testScope)
      advance()

      coordinator.handlePreviewTheme(SlateTheme)

      assertThat(coordinator.themeState.value.previewTheme).isEqualTo(SlateTheme)
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(SlateTheme)
    }

    @Test
    fun `null clears preview, displayTheme returns currentTheme`() {
      coordinator.initialize(testScope)
      advance()

      coordinator.handlePreviewTheme(SlateTheme)
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(SlateTheme)

      coordinator.handlePreviewTheme(null)
      assertThat(coordinator.themeState.value.previewTheme).isNull()
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(MinimalistTheme)
    }
  }

  @Nested
  @DisplayName("handleCycleThemeMode")
  inner class HandleCycleThemeMode {

    @Test
    fun `cycles through all 5 modes`() =
      runTest(testDispatcher) {
        coordinator.initialize(testScope)
        advance()

        // Start at SYSTEM
        assertThat(coordinator.themeState.value.autoSwitchMode).isEqualTo(AutoSwitchMode.SYSTEM)

        coordinator.handleCycleThemeMode()
        assertThat(coordinator.themeState.value.autoSwitchMode)
          .isEqualTo(AutoSwitchMode.SOLAR_AUTO)

        coordinator.handleCycleThemeMode()
        assertThat(coordinator.themeState.value.autoSwitchMode)
          .isEqualTo(AutoSwitchMode.ILLUMINANCE_AUTO)

        coordinator.handleCycleThemeMode()
        assertThat(coordinator.themeState.value.autoSwitchMode).isEqualTo(AutoSwitchMode.LIGHT)

        coordinator.handleCycleThemeMode()
        assertThat(coordinator.themeState.value.autoSwitchMode).isEqualTo(AutoSwitchMode.DARK)

        coordinator.handleCycleThemeMode()
        assertThat(coordinator.themeState.value.autoSwitchMode).isEqualTo(AutoSwitchMode.SYSTEM)

        // Verify each cycle persisted to repository
        coVerify(exactly = 5) { mockPrefsRepo.setAutoSwitchMode(any()) }
      }
  }

  @Nested
  @DisplayName("displayTheme derivation")
  inner class DisplayTheme {

    @Test
    fun `preview takes priority over currentTheme`() {
      coordinator.initialize(testScope)
      advance()

      // currentTheme is minimalist (light mode)
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(MinimalistTheme)

      // Set preview to slate
      coordinator.handlePreviewTheme(SlateTheme)
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(SlateTheme)

      // Change dark mode (currentTheme changes to slate too), preview still wins
      isDarkActiveFlow.value = true
      advance()
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(SlateTheme)

      // Clear preview, now currentTheme (slate from dark mode) shows through
      coordinator.handlePreviewTheme(null)
      assertThat(coordinator.themeState.value.displayTheme).isEqualTo(SlateTheme)
    }
  }
}
