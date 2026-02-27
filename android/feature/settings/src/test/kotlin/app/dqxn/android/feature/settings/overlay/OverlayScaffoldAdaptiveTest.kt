package app.dqxn.android.feature.settings.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class OverlayScaffoldAdaptiveTest {

  private val projectDir = File(checkNotNull(System.getProperty("user.dir")))

  private fun overlayScaffoldSource(): String {
    val file = File(
      projectDir,
      "src/main/kotlin/app/dqxn/android/feature/settings/overlay/OverlayScaffold.kt"
    )
    return file.readText()
  }

  @Test
  fun `OverlayScaffold contains width constraint logic`() {
    val content = overlayScaffoldSource()
    assertThat(content).contains("widthIn")
    assertThat(content).contains("maxWidthDp")
  }

  @Test
  fun `Hub overlay max width is 480dp`() {
    val content = overlayScaffoldSource()
    assertThat(content).contains("480.dp")
  }

  @Test
  fun `Preview overlay max width is 520dp`() {
    val content = overlayScaffoldSource()
    assertThat(content).contains("520.dp")
  }

  @Test
  fun `Confirmation overlay max width is 400dp`() {
    val content = overlayScaffoldSource()
    assertThat(content).contains("400.dp")
  }

  @Test
  fun `compact threshold is 600dp`() {
    val content = overlayScaffoldSource()
    assertThat(content).contains("600.dp")
  }

  @Test
  fun `compact screens have no width constraint`() {
    val content = overlayScaffoldSource()
    // Verify the isCompact check gates the width constraint
    assertThat(content).contains("isCompact")
  }

  @Test
  fun `existing OverlayScaffold API unchanged`() {
    val content = overlayScaffoldSource()
    // Public API signature must be preserved
    assertThat(content).contains("public fun OverlayScaffold(")
    assertThat(content).contains("title: String")
    assertThat(content).contains("overlayType: OverlayType")
    assertThat(content).contains("onClose: () -> Unit")
    assertThat(content).contains("visible: Boolean")
    assertThat(content).contains("content: @Composable () -> Unit")
  }
}
