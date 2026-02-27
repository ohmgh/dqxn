package app.dqxn.android.feature.dashboard.grid

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class ConfigurationBoundaryDetectorTest {

  private val windowLayoutInfoFlow = MutableSharedFlow<WindowLayoutInfo>(replay = 1)
  private val windowInfoTracker: WindowInfoTracker = mockk {
    every { windowLayoutInfo(any<Activity>()) } returns windowLayoutInfoFlow
  }

  private val detector =
    ConfigurationBoundaryDetector(
      windowInfoTracker = windowInfoTracker,
      logger = NoOpLogger,
    )

  private val mockActivity: Activity = mockk(relaxed = true)
  private val mockResources: Resources = mockk(relaxed = true)
  private val mockConfiguration: Configuration = Configuration()
  private val mockDisplayMetrics: DisplayMetrics = DisplayMetrics()

  @BeforeEach
  fun setUp() {
    every { mockActivity.resources } returns mockResources
    every { mockResources.configuration } returns mockConfiguration
    every { mockResources.displayMetrics } returns mockDisplayMetrics
  }

  @Test
  fun `foldable device emits fold boundary from FoldingFeature`() = runTest {
    val foldBounds =
      android.graphics.Rect().apply {
        left = 0
        top = 480
        right = 1080
        bottom = 500
      }
    val foldingFeature: FoldingFeature = mockk { every { bounds } returns foldBounds }
    val layoutInfo: WindowLayoutInfo = mockk {
      every { displayFeatures } returns listOf(foldingFeature)
    }

    val observeJob = Job(coroutineContext[Job])
    detector.observe(mockActivity, this + observeJob)
    testScheduler.runCurrent()
    windowLayoutInfoFlow.emit(layoutInfo)
    testScheduler.runCurrent()

    val boundaries = detector.boundaries.value
    assertThat(boundaries).hasSize(1)
    assertThat(boundaries.first().name).isEqualTo("fold")
    assertThat(boundaries.first().rect.left).isEqualTo(0)
    assertThat(boundaries.first().rect.top).isEqualTo(480)
    assertThat(boundaries.first().rect.right).isEqualTo(1080)
    assertThat(boundaries.first().rect.bottom).isEqualTo(500)

    observeJob.cancel()
  }

  @Test
  fun `non-foldable device emits alternate orientation boundary`() = runTest {
    val layoutInfo: WindowLayoutInfo = mockk { every { displayFeatures } returns emptyList() }

    mockConfiguration.orientation = Configuration.ORIENTATION_LANDSCAPE
    mockDisplayMetrics.widthPixels = 1920
    mockDisplayMetrics.heightPixels = 1080

    val observeJob = Job(coroutineContext[Job])
    detector.observe(mockActivity, this + observeJob)
    testScheduler.runCurrent()
    windowLayoutInfoFlow.emit(layoutInfo)
    testScheduler.runCurrent()

    val boundaries = detector.boundaries.value
    assertThat(boundaries).hasSize(1)
    assertThat(boundaries.first().name).isEqualTo("portrait")
    // Alternate orientation swaps width/height
    assertThat(boundaries.first().rect.right).isEqualTo(1080)
    assertThat(boundaries.first().rect.bottom).isEqualTo(1920)

    observeJob.cancel()
  }

  @Test
  fun `fixed orientation device emits empty boundaries`() = runTest {
    val layoutInfo: WindowLayoutInfo = mockk { every { displayFeatures } returns emptyList() }

    mockConfiguration.orientation = Configuration.ORIENTATION_UNDEFINED
    mockDisplayMetrics.widthPixels = 1920
    mockDisplayMetrics.heightPixels = 1080

    val observeJob = Job(coroutineContext[Job])
    detector.observe(mockActivity, this + observeJob)
    testScheduler.runCurrent()
    windowLayoutInfoFlow.emit(layoutInfo)
    testScheduler.runCurrent()

    assertThat(detector.boundaries.value).isEmpty()

    observeJob.cancel()
  }

  @Test
  fun `boundary updates on configuration change`() = runTest {
    val foldBounds1 =
      android.graphics.Rect().apply {
        left = 0
        top = 480
        right = 1080
        bottom = 500
      }
    val fold1: FoldingFeature = mockk { every { bounds } returns foldBounds1 }
    val layoutInfo1: WindowLayoutInfo = mockk { every { displayFeatures } returns listOf(fold1) }

    val foldBounds2 =
      android.graphics.Rect().apply {
        left = 0
        top = 960
        right = 1080
        bottom = 980
      }
    val fold2: FoldingFeature = mockk { every { bounds } returns foldBounds2 }
    val layoutInfo2: WindowLayoutInfo = mockk { every { displayFeatures } returns listOf(fold2) }

    val observeJob = Job(coroutineContext[Job])
    detector.observe(mockActivity, this + observeJob)
    testScheduler.runCurrent()

    windowLayoutInfoFlow.emit(layoutInfo1)
    testScheduler.runCurrent()
    assertThat(detector.boundaries.value.first().rect.top).isEqualTo(480)

    windowLayoutInfoFlow.emit(layoutInfo2)
    testScheduler.runCurrent()
    assertThat(detector.boundaries.value.first().rect.top).isEqualTo(960)

    observeJob.cancel()
  }
}
