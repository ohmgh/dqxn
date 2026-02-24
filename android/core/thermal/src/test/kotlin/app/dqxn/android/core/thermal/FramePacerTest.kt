package app.dqxn.android.core.thermal

import android.view.Window
import android.view.WindowManager
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FramePacerTest {

  private val window: Window = mockk(relaxed = true)
  private val params: WindowManager.LayoutParams = WindowManager.LayoutParams()
  private lateinit var framePacer: FramePacer

  @BeforeEach
  fun setUp() {
    every { window.attributes } returns params
    framePacer = FramePacer(NoOpLogger)
  }

  @Test
  fun `applyFrameRate sets preferredRefreshRate on window params`() {
    framePacer.applyFrameRate(window, 30f)

    val captured = slot<WindowManager.LayoutParams>()
    verify { window.attributes = capture(captured) }
    assertThat(captured.captured.preferredRefreshRate).isEqualTo(30f)
  }

  @Test
  fun `applyFrameRate no-ops when rate unchanged`() {
    framePacer.applyFrameRate(window, 60f)
    framePacer.applyFrameRate(window, 60f)

    // Only one set -- the second call should be a no-op.
    verify(exactly = 1) { window.attributes = any() }
  }

  @Test
  fun `applyFrameRate updates when rate changes`() {
    framePacer.applyFrameRate(window, 60f)
    framePacer.applyFrameRate(window, 30f)

    verify(exactly = 2) { window.attributes = any() }
  }

  @Test
  fun `reset sets preferredRefreshRate to 0`() {
    framePacer.applyFrameRate(window, 45f)
    framePacer.reset(window)

    val captured = mutableListOf<WindowManager.LayoutParams>()
    verify(exactly = 2) { window.attributes = capture(captured) }
    // Last captured params should have rate = 0 (reset).
    assertThat(captured.last().preferredRefreshRate).isEqualTo(0f)
  }

  @Test
  fun `reset no-ops when no rate was applied`() {
    framePacer.reset(window)
    verify(exactly = 0) { window.attributes = any() }
  }

  @Test
  fun `applyFrameRate works after reset`() {
    framePacer.applyFrameRate(window, 60f)
    framePacer.reset(window)
    framePacer.applyFrameRate(window, 45f)

    verify(exactly = 3) { window.attributes = any() }
  }
}
