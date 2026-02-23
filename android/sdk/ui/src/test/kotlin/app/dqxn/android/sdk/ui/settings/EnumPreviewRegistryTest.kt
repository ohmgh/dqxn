package app.dqxn.android.sdk.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnumPreviewRegistryTest {

  @get:Rule val composeTestRule = createComposeRule()

  private enum class TestEnum {
    ALPHA,
    BETA,
  }

  private enum class UnregisteredEnum {
    GAMMA,
  }

  private val registeredPreview: @Composable (Any) -> Unit = { value ->
    Text("Preview: ${(value as TestEnum).name}")
  }

  private val registry = EnumPreviewRegistry(previews = mapOf(TestEnum::class to registeredPreview))

  @Test
  fun `hasPreviews returns true for registered enum`() {
    assertThat(registry.hasPreviews(TestEnum::class)).isTrue()
  }

  @Test
  fun `hasPreviews returns false for unregistered enum`() {
    assertThat(registry.hasPreviews(UnregisteredEnum::class)).isFalse()
  }

  @Test
  fun `Preview renders registered composable`() {
    composeTestRule.setContent { registry.Preview(TestEnum::class, TestEnum.ALPHA) }
    composeTestRule.onNodeWithText("Preview: ALPHA").assertIsDisplayed()
  }

  @Test
  fun `Preview falls back to text for unregistered enum`() {
    composeTestRule.setContent { registry.Preview(UnregisteredEnum::class, UnregisteredEnum.GAMMA) }
    composeTestRule.onNodeWithText("GAMMA").assertIsDisplayed()
  }

  @Test
  fun `empty registry has no previews`() {
    val emptyRegistry = EnumPreviewRegistry(previews = emptyMap())
    assertThat(emptyRegistry.hasPreviews(TestEnum::class)).isFalse()
  }
}
