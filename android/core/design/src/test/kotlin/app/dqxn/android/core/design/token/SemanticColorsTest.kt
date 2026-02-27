package app.dqxn.android.core.design.token

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SemanticColorsTest {

  @Test
  fun `Info color is non-transparent`() {
    assertThat(SemanticColors.Info.alpha).isGreaterThan(0f)
  }

  @Test
  fun `Warning color is non-transparent`() {
    assertThat(SemanticColors.Warning.alpha).isGreaterThan(0f)
  }

  @Test
  fun `Success color is non-transparent`() {
    assertThat(SemanticColors.Success.alpha).isGreaterThan(0f)
  }

  @Test
  fun `Error color is non-transparent`() {
    assertThat(SemanticColors.Error.alpha).isGreaterThan(0f)
  }

  @Test
  fun `all four semantic colors have distinct values`() {
    val colors =
      setOf(
        SemanticColors.Info,
        SemanticColors.Warning,
        SemanticColors.Success,
        SemanticColors.Error
      )
    assertThat(colors).hasSize(4)
  }
}
