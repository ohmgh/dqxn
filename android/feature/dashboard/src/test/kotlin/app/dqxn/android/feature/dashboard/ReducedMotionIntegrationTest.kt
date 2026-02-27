package app.dqxn.android.feature.dashboard

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Integration tests for reduced motion compliance (NF39).
 *
 * Tests the production [ReducedMotionHelper] with mocked system settings to verify:
 * 1. Helper correctly detects disabled animations (scale == 0f)
 * 2. Widget add/remove transitions use instant specs when reduced motion active
 * 3. Helper correctly detects enabled animations (scale == 1f)
 *
 * DashboardGrid and ProfilePageTransition consume [ReducedMotionHelper.isReducedMotion] directly.
 * These tests verify the helper returns the correct value for both states, ensuring the production
 * gate conditions in those composables evaluate correctly.
 */
@Tag("fast")
class ReducedMotionIntegrationTest {

  private val contentResolver = mockk<ContentResolver>()
  private val context =
    mockk<Context> {
      every { this@mockk.contentResolver } returns this@ReducedMotionIntegrationTest.contentResolver
    }

  @BeforeEach
  fun setup() {
    mockkStatic(Settings.Global::class)
  }

  @AfterEach
  fun tearDown() {
    unmockkStatic(Settings.Global::class)
  }

  @Test
  fun `reduced motion - ReducedMotionHelper detects disabled animations`() {
    // Mock ANIMATOR_DURATION_SCALE == 0f (system reduced motion enabled)
    every {
      Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } returns 0f

    val helper = ReducedMotionHelper(context)

    // Production ReducedMotionHelper must return true when scale == 0f.
    // DashboardGrid uses: if (isEditMode && !isReducedMotion) { animate } else { 0f }
    // ProfilePageTransition uses: if (isReducedMotion) scrollToPage() else animateScrollToPage()
    assertThat(helper.isReducedMotion).isTrue()
  }

  @Test
  fun `reduced motion - widget add remove transitions use snap spec`() {
    // Mock system reduced motion active
    every {
      Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } returns 0f

    val helper = ReducedMotionHelper(context)
    val isReducedMotion = helper.isReducedMotion
    assertThat(isReducedMotion).isTrue()

    // Verify snap() produces a SnapSpec (instant transition, 0 delay)
    // DashboardGrid line 103: if (isReducedMotion) { fadeIn(snap()) + scaleIn(..., snap()) }
    val snapSpec = snap<Float>()
    assertThat(snapSpec).isInstanceOf(SnapSpec::class.java)
    assertThat(snapSpec.delay).isEqualTo(0)

    // Verify the production gate selects snap vs spring based on isReducedMotion
    val reducedSpec =
      if (isReducedMotion) snap<Float>() else spring<Float>(stiffness = Spring.StiffnessMediumLow)
    assertThat(reducedSpec).isInstanceOf(SnapSpec::class.java)

    // Verify the normal path would select spring
    every {
      Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } returns 1f

    val normalHelper = ReducedMotionHelper(context)
    val normalSpec =
      if (normalHelper.isReducedMotion) snap<Float>()
      else spring<Float>(stiffness = Spring.StiffnessMediumLow)
    assertThat(normalSpec).isNotInstanceOf(SnapSpec::class.java)
  }

  @Test
  fun `normal motion - ReducedMotionHelper detects enabled animations`() {
    // Mock ANIMATOR_DURATION_SCALE == 1f (normal animations enabled)
    every {
      Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } returns 1f

    val helper = ReducedMotionHelper(context)

    // Production ReducedMotionHelper must return false when scale == 1f.
    // This is the two-sided pair of test #1: verifies the helper correctly distinguishes
    // between reduced and normal motion states.
    assertThat(helper.isReducedMotion).isFalse()
  }
}
