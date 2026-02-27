package app.dqxn.android.core.design.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.ui.theme.MinimalistTheme
import app.dqxn.android.sdk.ui.theme.SlateTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeAutoSwitchEngineTest {

  private val scheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(scheduler)
  private val engineScope = CoroutineScope(testDispatcher)
  private val mockContext = mockk<Context>(relaxed = true)
  private val mockResources = mockk<Resources>()
  private val mockConfiguration = Configuration()

  private lateinit var engine: ThemeAutoSwitchEngine
  private lateinit var builtInThemes: BuiltInThemes

  @BeforeEach
  fun setUp() {
    mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_NO
    every { mockContext.resources } returns mockResources
    every { mockResources.configuration } returns mockConfiguration

    val parser =
      ThemeJsonParser(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, NoOpLogger)
    builtInThemes = BuiltInThemes(parser)

    engine =
      ThemeAutoSwitchEngine(
        context = mockContext,
        scope = engineScope,
        logger = NoOpLogger,
        builtInThemes = builtInThemes,
      )
  }

  @AfterEach
  fun tearDown() {
    engineScope.cancel()
  }

  private fun advance() {
    scheduler.advanceUntilIdle()
  }

  @Nested
  @DisplayName("isDarkActive")
  inner class IsDarkActive {

    @Test
    fun `LIGHT mode always returns false regardless of system mode`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.LIGHT)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `DARK mode always returns true regardless of system mode`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.DARK)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isTrue()
    }

    @Test
    fun `SYSTEM mode follows system dark mode value - light`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.SYSTEM)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `SYSTEM mode with night mode returns true`() {
      mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_YES
      // Re-create engine with dark system mode
      engine =
        ThemeAutoSwitchEngine(
          context = mockContext,
          scope = engineScope,
          logger = NoOpLogger,
          builtInThemes = builtInThemes,
        )
      advance()

      val modeFlow = MutableStateFlow(AutoSwitchMode.SYSTEM)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isTrue()
    }

    @Test
    fun `SOLAR_AUTO with no bound solar provider falls back to SYSTEM`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.SOLAR_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `SOLAR_AUTO with bound daytime=true returns false (light)`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.SOLAR_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )

      val solarFlow = MutableStateFlow(true)
      engine.bindSolarDaytime(solarFlow)
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `SOLAR_AUTO with bound daytime=false returns true (dark)`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.SOLAR_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )

      val solarFlow = MutableStateFlow(false)
      engine.bindSolarDaytime(solarFlow)
      advance()

      assertThat(engine.isDarkActive.value).isTrue()
    }

    @Test
    fun `ILLUMINANCE_AUTO with no bound provider falls back to SYSTEM`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.ILLUMINANCE_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `ILLUMINANCE_AUTO with bound lux below threshold returns true (dark)`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.ILLUMINANCE_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )

      val luxFlow = MutableStateFlow(100f)
      engine.bindIlluminance(luxFlow)
      advance()

      assertThat(engine.isDarkActive.value).isTrue()
    }

    @Test
    fun `ILLUMINANCE_AUTO with bound lux above threshold returns false (light)`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.ILLUMINANCE_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )

      val luxFlow = MutableStateFlow(300f)
      engine.bindIlluminance(luxFlow)
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `Eagerly started - isDarkActive has value immediately after construction`() {
      // The initial value is set synchronously -- no advance needed
      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `mode switching - DARK to LIGHT changes isDarkActive`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.DARK)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.isDarkActive.value).isTrue()

      modeFlow.value = AutoSwitchMode.LIGHT
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `unbound preferences uses defaults - SYSTEM mode`() {
      advance()
      // Default mode is SYSTEM, system is light -> not dark
      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `setAutoSwitchModeEagerly updates isDarkActive immediately`() {
      engine.setAutoSwitchModeEagerly(AutoSwitchMode.DARK)
      advance()

      assertThat(engine.isDarkActive.value).isTrue()

      engine.setAutoSwitchModeEagerly(AutoSwitchMode.LIGHT)
      advance()

      assertThat(engine.isDarkActive.value).isFalse()
    }

    @Test
    fun `setIlluminanceThresholdEagerly updates isDarkActive when in ILLUMINANCE_AUTO`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.ILLUMINANCE_AUTO)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )

      val luxFlow = MutableStateFlow(150f)
      engine.bindIlluminance(luxFlow)
      advance()

      // 150 < 200 -> dark
      assertThat(engine.isDarkActive.value).isTrue()

      engine.setIlluminanceThresholdEagerly(100f)
      advance()

      // 150 > 100 -> light
      assertThat(engine.isDarkActive.value).isFalse()
    }
  }

  @Nested
  @DisplayName("activeTheme")
  inner class ActiveTheme {

    @Test
    fun `resolves slate theme when dark is active`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.DARK)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.activeTheme.value).isEqualTo(SlateTheme)
    }

    @Test
    fun `resolves minimalist theme when light is active`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.LIGHT)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.activeTheme.value).isEqualTo(MinimalistTheme)
    }

    @Test
    fun `falls back to slate when unknown dark theme ID`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.DARK)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("minimalist"),
        darkThemeId = MutableStateFlow("nonexistent-theme"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.activeTheme.value).isEqualTo(SlateTheme)
    }

    @Test
    fun `falls back to minimalist when unknown light theme ID`() {
      val modeFlow = MutableStateFlow(AutoSwitchMode.LIGHT)
      engine.bindPreferences(
        autoSwitchMode = modeFlow,
        lightThemeId = MutableStateFlow("nonexistent-theme"),
        darkThemeId = MutableStateFlow("slate"),
        illuminanceThreshold = MutableStateFlow(200f),
      )
      advance()

      assertThat(engine.activeTheme.value).isEqualTo(MinimalistTheme)
    }
  }
}
