package app.dqxn.android.data.provider

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
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
class ProviderSettingsStoreTest {

  @TempDir lateinit var tempDir: Path

  private lateinit var dataStore: DataStore<Preferences>
  private lateinit var testScope: TestScope
  private lateinit var store: ProviderSettingsStoreImpl

  @BeforeEach
  fun setup() {
    val testDispatcher = StandardTestDispatcher()
    testScope = TestScope(testDispatcher)
    dataStore =
      PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir.toFile(), "test_provider_settings.preferences_pb") },
      )
    store = ProviderSettingsStoreImpl(dataStore)
  }

  // ---------------------------------------------------------------------------
  // Basic get/set
  // ---------------------------------------------------------------------------

  @Test
  fun `setSetting and getSetting round-trips string`() =
    testScope.runTest {
      store.setSetting("essentials", "gps-speed", "unit", "mph")
      store.getSetting("essentials", "gps-speed", "unit").test {
        assertThat(awaitItem()).isEqualTo("mph")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `getSetting returns null for missing key`() =
    testScope.runTest {
      store.getSetting("essentials", "gps-speed", "nonexistent").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Type-prefixed round-trip
  // ---------------------------------------------------------------------------

  @Test
  fun `round-trips Int value`() =
    testScope.runTest {
      store.setSetting("pack", "prov", "count", 42)
      store.getSetting("pack", "prov", "count").test {
        assertThat(awaitItem()).isEqualTo(42)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `round-trips Float value`() =
    testScope.runTest {
      store.setSetting("pack", "prov", "ratio", 3.14f)
      store.getSetting("pack", "prov", "ratio").test {
        assertThat(awaitItem()).isEqualTo(3.14f)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `round-trips Boolean value`() =
    testScope.runTest {
      store.setSetting("pack", "prov", "enabled", true)
      store.getSetting("pack", "prov", "enabled").test {
        assertThat(awaitItem()).isEqualTo(true)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `round-trips Long value`() =
    testScope.runTest {
      store.setSetting("pack", "prov", "timestamp", 1234567890123L)
      store.getSetting("pack", "prov", "timestamp").test {
        assertThat(awaitItem()).isEqualTo(1234567890123L)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `round-trips Double value`() =
    testScope.runTest {
      store.setSetting("pack", "prov", "precision", 2.718281828)
      store.getSetting("pack", "prov", "precision").test {
        assertThat(awaitItem()).isEqualTo(2.718281828)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `round-trips null value`() =
    testScope.runTest {
      store.setSetting("pack", "prov", "key", "initial")
      store.setSetting("pack", "prov", "key", null)
      store.getSetting("pack", "prov", "key").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Namespace isolation
  // ---------------------------------------------------------------------------

  @Test
  fun `writing to one provider does not affect another`() =
    testScope.runTest {
      store.setSetting("essentials", "gps-speed", "unit", "mph")
      store.setSetting("essentials", "compass", "style", "modern")

      store.getSetting("essentials", "gps-speed", "unit").test {
        assertThat(awaitItem()).isEqualTo("mph")
        cancelAndIgnoreRemainingEvents()
      }
      store.getSetting("essentials", "compass", "style").test {
        assertThat(awaitItem()).isEqualTo("modern")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `settings from different packs are isolated`() =
    testScope.runTest {
      store.setSetting("essentials", "speed", "unit", "mph")
      store.setSetting("plus", "speed", "unit", "kmh")

      store.getSetting("essentials", "speed", "unit").test {
        assertThat(awaitItem()).isEqualTo("mph")
        cancelAndIgnoreRemainingEvents()
      }
      store.getSetting("plus", "speed", "unit").test {
        assertThat(awaitItem()).isEqualTo("kmh")
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // clearSettings
  // ---------------------------------------------------------------------------

  @Test
  fun `clearSettings removes only target provider settings`() =
    testScope.runTest {
      store.setSetting("essentials", "gps-speed", "unit", "mph")
      store.setSetting("essentials", "gps-speed", "interval", 1000)
      store.setSetting("essentials", "compass", "style", "modern")

      store.clearSettings("essentials", "gps-speed")

      store.getSetting("essentials", "gps-speed", "unit").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
      store.getSetting("essentials", "gps-speed", "interval").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
      // Compass settings should be untouched
      store.getSetting("essentials", "compass", "style").test {
        assertThat(awaitItem()).isEqualTo("modern")
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // getAllSettings
  // ---------------------------------------------------------------------------

  @Test
  fun `getAllSettings returns all keys for specific provider`() =
    testScope.runTest {
      store.setSetting("essentials", "gps-speed", "unit", "mph")
      store.setSetting("essentials", "gps-speed", "interval", 1000)
      store.setSetting("essentials", "compass", "style", "modern")

      store.getAllSettings("essentials", "gps-speed").test {
        val settings = awaitItem()
        assertThat(settings).hasSize(2)
        assertThat(settings["unit"]).isEqualTo("mph")
        assertThat(settings["interval"]).isEqualTo(1000)
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // clearAll
  // ---------------------------------------------------------------------------

  @Test
  fun `clearAll removes all settings across all packs and providers`() =
    testScope.runTest {
      store.setSetting("essentials", "gps-speed", "unit", "mph")
      store.setSetting("essentials", "compass", "style", "modern")
      store.setSetting("plus", "weather", "location", "auto")

      store.clearAll()

      store.getSetting("essentials", "gps-speed", "unit").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
      store.getSetting("essentials", "compass", "style").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
      store.getSetting("plus", "weather", "location").test {
        assertThat(awaitItem()).isNull()
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Legacy fallback
  // ---------------------------------------------------------------------------

  @Test
  fun `deserializeValue treats unprefixed strings as raw string`() {
    // Direct test of SettingsSerialization legacy fallback
    assertThat(SettingsSerialization.deserializeValue("some raw value")).isEqualTo("some raw value")
  }
}
