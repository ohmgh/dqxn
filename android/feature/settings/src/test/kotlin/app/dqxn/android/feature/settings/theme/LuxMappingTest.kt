package app.dqxn.android.feature.settings.theme

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs

@DisplayName("LuxMapping")
internal class LuxMappingTest {

  @Nested
  @DisplayName("luxToPosition")
  inner class LuxToPositionTests {

    @Test
    fun `0 lux maps to 0 or near-zero`() {
      val position = luxToPosition(0f)
      assertWithMessage("0 lux position").that(position).isWithin(0.01f).of(0f)
    }

    @Test
    fun `MAX_LUX maps to 1`() {
      val position = luxToPosition(MAX_LUX)
      assertWithMessage("MAX_LUX position").that(position).isWithin(0.01f).of(1f)
    }

    @Test
    fun `negative lux clamps to 0`() {
      val position = luxToPosition(-100f)
      assertWithMessage("negative lux position").that(position).isWithin(0.01f).of(0f)
    }

    @Test
    fun `typical indoor 300 lux is between 0 and 1`() {
      val position = luxToPosition(300f)
      assertWithMessage("300 lux > 0").that(position).isGreaterThan(0f)
      assertWithMessage("300 lux < 1").that(position).isLessThan(1f)
    }

    @Test
    fun `MIN_LUX maps to 0`() {
      val position = luxToPosition(MIN_LUX)
      assertWithMessage("MIN_LUX position").that(position).isWithin(0.01f).of(0f)
    }

    @Test
    fun `monotonically increasing`() {
      val luxValues = listOf(1f, 10f, 100f, 1000f, 10_000f)
      val positions = luxValues.map { luxToPosition(it) }
      for (i in 0 until positions.size - 1) {
        assertWithMessage("${luxValues[i]} < ${luxValues[i + 1]}")
          .that(positions[i])
          .isLessThan(positions[i + 1])
      }
    }
  }

  @Nested
  @DisplayName("positionToLux")
  inner class PositionToLuxTests {

    @Test
    fun `position 0 maps to MIN_LUX or less`() {
      val lux = positionToLux(0f)
      assertWithMessage("position 0 lux").that(lux).isAtMost(MIN_LUX)
    }

    @Test
    fun `position 1 maps to MAX_LUX`() {
      val lux = positionToLux(1f)
      assertWithMessage("position 1.0 lux").that(lux).isWithin(1f).of(MAX_LUX)
    }

    @Test
    fun `negative position clamps to minimum`() {
      val lux = positionToLux(-0.5f)
      assertWithMessage("negative position lux").that(lux).isAtMost(MIN_LUX)
    }

    @Test
    fun `position above 1 clamps to MAX_LUX`() {
      val lux = positionToLux(1.5f)
      assertWithMessage("above 1.0 position lux").that(lux).isAtMost(MAX_LUX)
    }
  }

  @Nested
  @DisplayName("Inverse property tests")
  inner class InversePropertyTests {

    @Test
    fun `positionToLux of luxToPosition round-trips within 1 percent`() {
      val luxValues = listOf(1f, 10f, 100f, 500f, 1000f, 5000f, 10_000f)

      for (lux in luxValues) {
        val position = luxToPosition(lux)
        val roundTripped = positionToLux(position)
        val relativeError = abs(roundTripped - lux) / lux
        assertWithMessage("round-trip for $lux lux (got $roundTripped, error=$relativeError)")
          .that(relativeError)
          .isLessThan(0.01f)
      }
    }

    @Test
    fun `luxToPosition of positionToLux round-trips within 0_01 tolerance`() {
      val positions = listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)

      for (pos in positions) {
        val lux = positionToLux(pos)
        val roundTripped = luxToPosition(lux)
        assertWithMessage("round-trip for position $pos (got $roundTripped)")
          .that(abs(roundTripped - pos))
          .isLessThan(0.01f)
      }
    }
  }
}
