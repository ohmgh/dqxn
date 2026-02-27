package app.dqxn.android.feature.dashboard.gesture

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ReducedMotionHelperTest {

  private val mockContentResolver = mockk<ContentResolver>()
  private val mockContext = mockk<Context> { every { contentResolver } returns mockContentResolver }

  private lateinit var helper: ReducedMotionHelper

  @BeforeEach
  fun setUp() {
    mockkStatic(Settings.Global::class)
    helper = ReducedMotionHelper(context = mockContext)
  }

  @AfterEach
  fun tearDown() {
    unmockkStatic(Settings.Global::class)
  }

  @Test
  fun `isReducedMotion returns true when animator_duration_scale is 0`() {
    every {
      Settings.Global.getFloat(
        mockContentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    } returns 0f

    assertThat(helper.isReducedMotion).isTrue()
  }

  @Test
  fun `isReducedMotion returns false when animator_duration_scale is 1`() {
    every {
      Settings.Global.getFloat(
        mockContentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    } returns 1f

    assertThat(helper.isReducedMotion).isFalse()
  }

  @Test
  fun `isReducedMotion returns false when setting is not found (default 1f)`() {
    // When the setting doesn't exist, getFloat returns the default (1f)
    every {
      Settings.Global.getFloat(
        mockContentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    } returns 1f

    assertThat(helper.isReducedMotion).isFalse()
  }
}
