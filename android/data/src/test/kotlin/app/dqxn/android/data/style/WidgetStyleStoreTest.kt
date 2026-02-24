package app.dqxn.android.data.style

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
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
class WidgetStyleStoreTest {

  @TempDir lateinit var tempDir: Path

  private lateinit var dataStore: DataStore<Preferences>
  private lateinit var testScope: TestScope
  private lateinit var store: WidgetStyleStoreImpl

  private val customStyle =
    WidgetStyle(
      backgroundStyle = BackgroundStyle.SOLID,
      opacity = 0.85f,
      showBorder = true,
      hasGlowEffect = true,
      cornerRadiusPercent = 50,
      rimSizePercent = 10,
      zLayer = 3,
    )

  @BeforeEach
  fun setup() {
    val testDispatcher = StandardTestDispatcher()
    testScope = TestScope(testDispatcher)
    dataStore =
      PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir.toFile(), "test_widget_styles.preferences_pb") },
      )
    store = WidgetStyleStoreImpl(dataStore)
  }

  @Test
  fun `setStyle then getStyle returns saved style`() =
    testScope.runTest {
      store.setStyle("widget-1", customStyle)
      store.getStyle("widget-1").test {
        val result = awaitItem()
        assertThat(result.backgroundStyle).isEqualTo(BackgroundStyle.SOLID)
        assertThat(result.opacity).isEqualTo(0.85f)
        assertThat(result.showBorder).isTrue()
        assertThat(result.hasGlowEffect).isTrue()
        assertThat(result.cornerRadiusPercent).isEqualTo(50)
        assertThat(result.rimSizePercent).isEqualTo(10)
        assertThat(result.zLayer).isEqualTo(3)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `getStyle for missing widget returns Default`() =
    testScope.runTest {
      store.getStyle("nonexistent").test {
        assertThat(awaitItem()).isEqualTo(WidgetStyle.Default)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `removeStyle reverts to Default`() =
    testScope.runTest {
      store.setStyle("widget-1", customStyle)
      store.removeStyle("widget-1")
      store.getStyle("widget-1").test {
        assertThat(awaitItem()).isEqualTo(WidgetStyle.Default)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `JSON round-trip preserves all WidgetStyle fields`() =
    testScope.runTest {
      store.setStyle("widget-2", customStyle)
      store.getStyle("widget-2").test {
        assertThat(awaitItem()).isEqualTo(customStyle)
        cancelAndIgnoreRemainingEvents()
      }
    }
}
