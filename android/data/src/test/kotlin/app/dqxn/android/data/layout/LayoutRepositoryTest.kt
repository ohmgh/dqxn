package app.dqxn.android.data.layout

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import app.dqxn.android.data.proto.DashboardStoreProto
import app.dqxn.android.data.serializer.DashboardStoreSerializer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.observability.crash.ErrorContext
import app.dqxn.android.sdk.observability.crash.ErrorReporter
import app.dqxn.android.sdk.observability.crash.WidgetErrorContext
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutRepositoryTest {

  @TempDir lateinit var tempDir: Path

  private lateinit var dataStore: DataStore<DashboardStoreProto>
  private lateinit var testScope: TestScope
  private lateinit var repo: LayoutRepositoryImpl

  private val noOpReporter =
    object : ErrorReporter {
      override fun reportNonFatal(e: Throwable, context: ErrorContext) {}

      override fun reportWidgetCrash(
        typeId: String,
        widgetId: String,
        context: WidgetErrorContext,
      ) {}
    }

  @BeforeEach
  fun setup() {
    val testDispatcher = StandardTestDispatcher()
    testScope = TestScope(testDispatcher)

    dataStore =
      DataStoreFactory.create(
        serializer = DashboardStoreSerializer,
        produceFile = { File(tempDir.toFile(), "test_dashboard_store.pb") },
      )

    repo =
      LayoutRepositoryImpl(
        dashboardDataStore = dataStore,
        logger = NoOpLogger,
        errorReporter = noOpReporter,
        ioDispatcher = testDispatcher,
        scope = testScope.backgroundScope,
      )
  }

  @Test
  fun `initial state has fallback profile`() =
    testScope.runTest {
      advanceUntilIdle()

      repo.profiles.test {
        val list = awaitItem()
        assertThat(list).isNotEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `createProfile adds new profile to list`() =
    testScope.runTest {
      advanceUntilIdle()

      val newId = repo.createProfile("Work")
      advanceUntilIdle()

      repo.profiles.test {
        val list = awaitItem()
        assertThat(list.map { it.profileId }).contains(newId)
        assertThat(list.find { it.profileId == newId }?.displayName).isEqualTo("Work")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `cloneProfile creates profile with same widgets but different instanceIds`() =
    testScope.runTest {
      advanceUntilIdle()

      // Get the initial active profile id
      val initialProfileId = repo.activeProfileId.first()

      // Add a widget to the initial profile
      val widget = createTestWidget("w1")
      repo.addWidget(widget)
      advanceUntilIdle()

      // Clone the profile
      val clonedId = repo.cloneProfile(initialProfileId, "Cloned")
      advanceUntilIdle()

      // Switch to cloned profile and check widgets
      repo.switchProfile(clonedId)
      advanceUntilIdle()

      repo.getActiveProfileWidgets().test {
        val widgets = awaitItem()
        // fallback clock + cloned w1
        val clonedW1 = widgets.find { it.typeId == "essentials:test" }
        assertThat(clonedW1).isNotNull()
        assertThat(clonedW1!!.instanceId).isNotEqualTo("w1")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `switchProfile changes activeProfileId flow`() =
    testScope.runTest {
      advanceUntilIdle()

      val newId = repo.createProfile("Secondary")
      advanceUntilIdle()

      repo.switchProfile(newId)
      advanceUntilIdle()

      repo.activeProfileId.test {
        assertThat(awaitItem()).isEqualTo(newId)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `deleteProfile removes profile from list`() =
    testScope.runTest {
      advanceUntilIdle()

      val newId = repo.createProfile("ToDelete")
      advanceUntilIdle()

      repo.deleteProfile(newId)
      advanceUntilIdle()

      repo.profiles.test {
        val list = awaitItem()
        assertThat(list.map { it.profileId }).doesNotContain(newId)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `deleteProfile on last profile throws IllegalStateException`() =
    testScope.runTest {
      advanceUntilIdle()

      val profileId = repo.activeProfileId.first()

      val result = runCatching { repo.deleteProfile(profileId) }
      assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

  @Test
  fun `addWidget adds widget to active profile`() =
    testScope.runTest {
      advanceUntilIdle()

      val widget = createTestWidget("new-widget")
      repo.addWidget(widget)
      advanceUntilIdle()

      repo.getActiveProfileWidgets().test {
        val widgets = awaitItem()
        assertThat(widgets.map { it.instanceId }).contains("new-widget")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `removeWidget removes widget from active profile`() =
    testScope.runTest {
      advanceUntilIdle()

      val widget = createTestWidget("to-remove")
      repo.addWidget(widget)
      advanceUntilIdle()

      repo.removeWidget("to-remove")
      advanceUntilIdle()

      repo.getActiveProfileWidgets().test {
        val widgets = awaitItem()
        assertThat(widgets.map { it.instanceId }).doesNotContain("to-remove")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `debounced save batches rapid mutations`() =
    testScope.runTest {
      advanceUntilIdle()

      val widget = createTestWidget("debounce-test")
      repo.addWidget(widget)
      advanceUntilIdle()

      // Rapid mutations without advancing time past debounce threshold
      for (i in 1..10) {
        repo.updateWidgetPosition("debounce-test", GridPosition(col = i, row = i))
      }

      // Now advance past debounce window (500ms + margin)
      advanceTimeBy(600)
      advanceUntilIdle()

      // Verify final position is the last one set
      repo.getActiveProfileWidgets().test {
        val widgets = awaitItem()
        val updated = widgets.find { it.instanceId == "debounce-test" }
        assertThat(updated?.position).isEqualTo(GridPosition(col = 10, row = 10))
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `updateWidget replaces widget in active profile`() =
    testScope.runTest {
      advanceUntilIdle()

      val widget = createTestWidget("update-test")
      repo.addWidget(widget)
      advanceUntilIdle()

      val updated = widget.copy(zIndex = 5)
      repo.updateWidget(updated)
      advanceUntilIdle()

      repo.getActiveProfileWidgets().test {
        val widgets = awaitItem()
        val found = widgets.find { it.instanceId == "update-test" }
        assertThat(found?.zIndex).isEqualTo(5)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `updateWidgetSize changes widget size`() =
    testScope.runTest {
      advanceUntilIdle()

      val widget = createTestWidget("size-test")
      repo.addWidget(widget)
      advanceUntilIdle()

      repo.updateWidgetSize("size-test", GridSize(widthUnits = 20, heightUnits = 15))
      advanceUntilIdle()

      repo.getActiveProfileWidgets().test {
        val widgets = awaitItem()
        val found = widgets.find { it.instanceId == "size-test" }
        assertThat(found?.size).isEqualTo(GridSize(widthUnits = 20, heightUnits = 15))
        cancelAndIgnoreRemainingEvents()
      }
    }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private fun createTestWidget(instanceId: String): DashboardWidgetInstance =
    DashboardWidgetInstance(
      instanceId = instanceId,
      typeId = "essentials:test",
      position = GridPosition(col = 0, row = 0),
      size = GridSize(widthUnits = 5, heightUnits = 5),
      style = WidgetStyle.Default,
      settings = persistentMapOf(),
      dataSourceBindings = persistentMapOf(),
      zIndex = 0,
    )
}
