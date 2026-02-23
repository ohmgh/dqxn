package app.dqxn.android.sdk.ui.layout

import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.dqxn.android.sdk.contracts.settings.SizeOption
import app.dqxn.android.sdk.contracts.settings.toMultiplier
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("InfoCardLayout")
class InfoCardLayoutTest {

  @Nested
  @DisplayName("SizeOption.toMultiplier()")
  inner class SizeOptionMultiplierTest {

    @Test
    fun `SMALL returns 0_75`() {
      assertThat(SizeOption.SMALL.toMultiplier()).isEqualTo(0.75f)
    }

    @Test
    fun `MEDIUM returns 1_0`() {
      assertThat(SizeOption.MEDIUM.toMultiplier()).isEqualTo(1.0f)
    }

    @Test
    fun `LARGE returns 1_25`() {
      assertThat(SizeOption.LARGE.toMultiplier()).isEqualTo(1.25f)
    }

    @Test
    fun `EXTRA_LARGE returns 1_5`() {
      assertThat(SizeOption.EXTRA_LARGE.toMultiplier()).isEqualTo(1.5f)
    }
  }

  @Nested
  @DisplayName("computeAllocation normalization")
  inner class NormalizationTest {

    private val availableHeight = 1000f
    private val spacerHeight = 10f

    @Test
    fun `all MEDIUM elements distribute equally at 80 percent`() {
      val allocation =
        computeAllocation(
          iconMultiplier = 1.0f, // MEDIUM
          topTextMultiplier = 1.0f,
          bottomTextMultiplier = 1.0f,
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      // 3 non-zero elements => 2 spacers => 1000 - 20 = 980 available
      // 80% of 980 = 784 allocatable
      // Each gets 784/3 = 261.33
      val expected = (availableHeight - 2 * spacerHeight) * NORMALIZATION_TARGET / 3f
      assertThat(allocation.iconPx).isWithin(0.01f).of(expected)
      assertThat(allocation.topTextPx).isWithin(0.01f).of(expected)
      assertThat(allocation.bottomTextPx).isWithin(0.01f).of(expected)
    }

    @Test
    fun `total allocation is 80 percent of available height minus spacers`() {
      val allocation =
        computeAllocation(
          iconMultiplier = SizeOption.SMALL.toMultiplier(),
          topTextMultiplier = SizeOption.LARGE.toMultiplier(),
          bottomTextMultiplier = SizeOption.MEDIUM.toMultiplier(),
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      val total = allocation.iconPx + allocation.topTextPx + allocation.bottomTextPx
      val expectedTotal = (availableHeight - 2 * spacerHeight) * NORMALIZATION_TARGET
      assertThat(total).isWithin(0.01f).of(expectedTotal)
    }

    @Test
    fun `proportions match weight ratios`() {
      // SMALL=0.75, LARGE=1.25, EXTRA_LARGE=1.5
      val allocation =
        computeAllocation(
          iconMultiplier = 0.75f,
          topTextMultiplier = 1.25f,
          bottomTextMultiplier = 1.5f,
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      // Icon should be 0.75/3.5 of total
      val totalWeight = 0.75f + 1.25f + 1.5f
      val total = allocation.iconPx + allocation.topTextPx + allocation.bottomTextPx
      assertThat(allocation.iconPx / total).isWithin(0.01f).of(0.75f / totalWeight)
      assertThat(allocation.topTextPx / total).isWithin(0.01f).of(1.25f / totalWeight)
      assertThat(allocation.bottomTextPx / total).isWithin(0.01f).of(1.5f / totalWeight)
    }

    @Test
    fun `zero multiplier element gets zero space`() {
      val allocation =
        computeAllocation(
          iconMultiplier = 0f,
          topTextMultiplier = 1.0f,
          bottomTextMultiplier = 0.75f,
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      assertThat(allocation.iconPx).isEqualTo(0f)
      // 2 non-zero => 1 spacer => 1000 - 10 = 990 available
      // 80% of 990 = 792 allocatable
      val expectedTop = (availableHeight - spacerHeight) * NORMALIZATION_TARGET * (1.0f / 1.75f)
      assertThat(allocation.topTextPx).isWithin(0.01f).of(expectedTop)
    }

    @Test
    fun `single non-zero element gets full 80 percent`() {
      val allocation =
        computeAllocation(
          iconMultiplier = 0f,
          topTextMultiplier = 1.0f,
          bottomTextMultiplier = 0f,
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      // 1 non-zero => 0 spacers
      val expected = availableHeight * NORMALIZATION_TARGET
      assertThat(allocation.topTextPx).isWithin(0.01f).of(expected)
      assertThat(allocation.iconPx).isEqualTo(0f)
      assertThat(allocation.bottomTextPx).isEqualTo(0f)
    }

    @Test
    fun `all zero multipliers returns zero allocation`() {
      val allocation =
        computeAllocation(
          iconMultiplier = 0f,
          topTextMultiplier = 0f,
          bottomTextMultiplier = 0f,
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      assertThat(allocation.iconPx).isEqualTo(0f)
      assertThat(allocation.topTextPx).isEqualTo(0f)
      assertThat(allocation.bottomTextPx).isEqualTo(0f)
    }

    @Test
    fun `zero available height returns zero allocation`() {
      val allocation =
        computeAllocation(
          iconMultiplier = 1.0f,
          topTextMultiplier = 1.0f,
          bottomTextMultiplier = 1.0f,
          availableHeightPx = 0f,
          spacerHeightPx = spacerHeight,
        )
      assertThat(allocation.iconPx).isEqualTo(0f)
      assertThat(allocation.topTextPx).isEqualTo(0f)
      assertThat(allocation.bottomTextPx).isEqualTo(0f)
    }

    @Test
    fun `all EXTRA_LARGE stress test distributes equally`() {
      val m = SizeOption.EXTRA_LARGE.toMultiplier()
      val allocation =
        computeAllocation(
          iconMultiplier = m,
          topTextMultiplier = m,
          bottomTextMultiplier = m,
          availableHeightPx = availableHeight,
          spacerHeightPx = spacerHeight,
        )
      val expected = (availableHeight - 2 * spacerHeight) * NORMALIZATION_TARGET / 3f
      assertThat(allocation.iconPx).isWithin(0.01f).of(expected)
      assertThat(allocation.topTextPx).isWithin(0.01f).of(expected)
      assertThat(allocation.bottomTextPx).isWithin(0.01f).of(expected)
    }
  }

  @Nested
  @DisplayName("getTightTextStyle")
  inner class TightTextStyleTest {

    @Test
    fun `eliminates font padding`() {
      val style = getTightTextStyle(16.sp)
      assertThat(style.platformStyle?.paragraphStyle?.includeFontPadding).isFalse()
    }

    @Test
    fun `line height matches font size`() {
      val style = getTightTextStyle(20.sp)
      assertThat(style.lineHeight).isEqualTo(20.sp)
    }

    @Test
    fun `line height style trims both and centers`() {
      val style = getTightTextStyle(14.sp)
      assertThat(style.lineHeightStyle?.trim).isEqualTo(LineHeightStyle.Trim.Both)
      assertThat(style.lineHeightStyle?.alignment).isEqualTo(LineHeightStyle.Alignment.Center)
    }
  }

  @Nested
  @DisplayName("parseLayoutMode and parseSizeOption")
  inner class ParsingTest {

    @Test
    fun `parseLayoutMode returns valid value`() {
      assertThat(parseLayoutMode("STANDARD"))
        .isEqualTo(app.dqxn.android.sdk.contracts.settings.InfoCardLayoutMode.STANDARD)
    }

    @Test
    fun `parseLayoutMode falls back to STANDARD for invalid input`() {
      assertThat(parseLayoutMode("INVALID"))
        .isEqualTo(app.dqxn.android.sdk.contracts.settings.InfoCardLayoutMode.STANDARD)
    }

    @Test
    fun `parseSizeOption returns valid value`() {
      assertThat(parseSizeOption("LARGE")).isEqualTo(SizeOption.LARGE)
    }

    @Test
    fun `parseSizeOption falls back to MEDIUM for invalid input`() {
      assertThat(parseSizeOption("INVALID")).isEqualTo(SizeOption.MEDIUM)
    }
  }
}
