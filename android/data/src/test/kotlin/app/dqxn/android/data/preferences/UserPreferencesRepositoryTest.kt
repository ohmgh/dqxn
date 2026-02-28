package app.dqxn.android.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

  @TempDir lateinit var tempDir: Path

  private lateinit var dataStore: DataStore<Preferences>
  private lateinit var testScope: TestScope
  private lateinit var repo: UserPreferencesRepositoryImpl

  @BeforeEach
  fun setup() {
    val testDispatcher = StandardTestDispatcher()
    testScope = TestScope(testDispatcher)

    dataStore =
      PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir.toFile(), "test_user_prefs.preferences_pb") },
      )

    repo = UserPreferencesRepositoryImpl(dataStore)
  }

  // ---------------------------------------------------------------------------
  // Default values
  // ---------------------------------------------------------------------------

  @Test
  fun `default autoSwitchMode is SYSTEM`() =
    testScope.runTest {
      repo.autoSwitchMode.test {
        assertThat(awaitItem()).isEqualTo(AutoSwitchMode.SYSTEM)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default lightThemeId is minimalist`() =
    testScope.runTest {
      repo.lightThemeId.test {
        assertThat(awaitItem()).isEqualTo("essentials:minimalist")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default darkThemeId is slate`() =
    testScope.runTest {
      repo.darkThemeId.test {
        assertThat(awaitItem()).isEqualTo("essentials:slate")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default illuminanceThreshold is 100f`() =
    testScope.runTest {
      repo.illuminanceThreshold.test {
        assertThat(awaitItem()).isEqualTo(100f)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default keepScreenOn is true`() =
    testScope.runTest {
      repo.keepScreenOn.test {
        assertThat(awaitItem()).isTrue()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default orientationLock is auto`() =
    testScope.runTest {
      repo.orientationLock.test {
        assertThat(awaitItem()).isEqualTo("auto")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default showStatusBar is false`() =
    testScope.runTest {
      repo.showStatusBar.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Setters
  // ---------------------------------------------------------------------------

  @Test
  fun `setAutoSwitchMode updates flow`() =
    testScope.runTest {
      repo.autoSwitchMode.test {
        assertThat(awaitItem()).isEqualTo(AutoSwitchMode.SYSTEM)

        repo.setAutoSwitchMode(AutoSwitchMode.SOLAR_AUTO)
        assertThat(awaitItem()).isEqualTo(AutoSwitchMode.SOLAR_AUTO)

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setLightThemeId updates flow`() =
    testScope.runTest {
      repo.lightThemeId.test {
        assertThat(awaitItem()).isEqualTo("essentials:minimalist")

        repo.setLightThemeId("custom-light")
        assertThat(awaitItem()).isEqualTo("custom-light")

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setDarkThemeId updates flow`() =
    testScope.runTest {
      repo.darkThemeId.test {
        assertThat(awaitItem()).isEqualTo("essentials:slate")

        repo.setDarkThemeId("custom-dark")
        assertThat(awaitItem()).isEqualTo("custom-dark")

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setIlluminanceThreshold updates flow`() =
    testScope.runTest {
      repo.illuminanceThreshold.test {
        assertThat(awaitItem()).isEqualTo(100f)

        repo.setIlluminanceThreshold(200f)
        assertThat(awaitItem()).isEqualTo(200f)

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setKeepScreenOn updates flow`() =
    testScope.runTest {
      repo.keepScreenOn.test {
        assertThat(awaitItem()).isTrue()

        repo.setKeepScreenOn(false)
        assertThat(awaitItem()).isFalse()

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setOrientationLock updates flow`() =
    testScope.runTest {
      repo.orientationLock.test {
        assertThat(awaitItem()).isEqualTo("auto")

        repo.setOrientationLock("landscape")
        assertThat(awaitItem()).isEqualTo("landscape")

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setShowStatusBar updates flow`() =
    testScope.runTest {
      repo.showStatusBar.test {
        assertThat(awaitItem()).isFalse()

        repo.setShowStatusBar(true)
        assertThat(awaitItem()).isTrue()

        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // analyticsConsent
  // ---------------------------------------------------------------------------

  @Test
  fun `default analyticsConsent is false`() =
    testScope.runTest {
      repo.analyticsConsent.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setAnalyticsConsent true updates flow`() =
    testScope.runTest {
      repo.analyticsConsent.test {
        assertThat(awaitItem()).isFalse()

        repo.setAnalyticsConsent(true)
        assertThat(awaitItem()).isTrue()

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setAnalyticsConsent false after true updates flow`() =
    testScope.runTest {
      repo.setAnalyticsConsent(true)

      repo.analyticsConsent.test {
        assertThat(awaitItem()).isTrue()

        repo.setAnalyticsConsent(false)
        assertThat(awaitItem()).isFalse()

        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // clearAll
  // ---------------------------------------------------------------------------

  @Test
  fun `clearAll resets all preferences to defaults`() =
    testScope.runTest {
      // Set non-default values
      repo.setAutoSwitchMode(AutoSwitchMode.SOLAR_AUTO)
      repo.setLightThemeId("neon")
      repo.setDarkThemeId("midnight")
      repo.setKeepScreenOn(false)
      repo.setAnalyticsConsent(true)

      // Clear
      repo.clearAll()

      // Verify all reverted to defaults
      repo.autoSwitchMode.test {
        assertThat(awaitItem()).isEqualTo(AutoSwitchMode.SYSTEM)
        cancelAndIgnoreRemainingEvents()
      }
      repo.lightThemeId.test {
        assertThat(awaitItem()).isEqualTo("essentials:minimalist")
        cancelAndIgnoreRemainingEvents()
      }
      repo.darkThemeId.test {
        assertThat(awaitItem()).isEqualTo("essentials:slate")
        cancelAndIgnoreRemainingEvents()
      }
      repo.keepScreenOn.test {
        assertThat(awaitItem()).isTrue()
        cancelAndIgnoreRemainingEvents()
      }
      repo.analyticsConsent.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Onboarding preferences
  // ---------------------------------------------------------------------------

  @Test
  fun `default hasCompletedOnboarding is false`() =
    testScope.runTest {
      repo.hasCompletedOnboarding.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setHasCompletedOnboarding persists true`() =
    testScope.runTest {
      repo.hasCompletedOnboarding.test {
        assertThat(awaitItem()).isFalse()

        repo.setHasCompletedOnboarding(true)
        assertThat(awaitItem()).isTrue()

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `default hasSeenDisclaimer is false`() =
    testScope.runTest {
      repo.hasSeenDisclaimer.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `setHasSeenDisclaimer persists true`() =
    testScope.runTest {
      repo.hasSeenDisclaimer.test {
        assertThat(awaitItem()).isFalse()

        repo.setHasSeenDisclaimer(true)
        assertThat(awaitItem()).isTrue()

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `hasSeenTip defaults to false for unknown key`() =
    testScope.runTest {
      repo.hasSeenTip("unknown_tip").test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `markTipSeen persists true for specific key`() =
    testScope.runTest {
      repo.hasSeenTip("edit_mode").test {
        assertThat(awaitItem()).isFalse()

        repo.markTipSeen("edit_mode")
        assertThat(awaitItem()).isTrue()

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `tip keys are independent`() =
    testScope.runTest {
      repo.markTipSeen("tip_a")

      repo.hasSeenTip("tip_a").test {
        assertThat(awaitItem()).isTrue()
        cancelAndIgnoreRemainingEvents()
      }

      repo.hasSeenTip("tip_b").test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Round-trip
  // ---------------------------------------------------------------------------

  @Test
  fun `round trip all preferences`() =
    testScope.runTest {
      repo.setAutoSwitchMode(AutoSwitchMode.ILLUMINANCE_AUTO)
      repo.setLightThemeId("neon")
      repo.setDarkThemeId("midnight")
      repo.setIlluminanceThreshold(300f)
      repo.setKeepScreenOn(false)
      repo.setOrientationLock("portrait")
      repo.setShowStatusBar(true)

      repo.autoSwitchMode.test {
        assertThat(awaitItem()).isEqualTo(AutoSwitchMode.ILLUMINANCE_AUTO)
        cancelAndIgnoreRemainingEvents()
      }
      repo.lightThemeId.test {
        assertThat(awaitItem()).isEqualTo("neon")
        cancelAndIgnoreRemainingEvents()
      }
      repo.darkThemeId.test {
        assertThat(awaitItem()).isEqualTo("midnight")
        cancelAndIgnoreRemainingEvents()
      }
      repo.illuminanceThreshold.test {
        assertThat(awaitItem()).isEqualTo(300f)
        cancelAndIgnoreRemainingEvents()
      }
      repo.keepScreenOn.test {
        assertThat(awaitItem()).isFalse()
        cancelAndIgnoreRemainingEvents()
      }
      repo.orientationLock.test {
        assertThat(awaitItem()).isEqualTo("portrait")
        cancelAndIgnoreRemainingEvents()
      }
      repo.showStatusBar.test {
        assertThat(awaitItem()).isTrue()
        cancelAndIgnoreRemainingEvents()
      }
    }
}
