package app.dqxn.android.feature.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

class WidgetPickerLayoutTest {

  /** Resolve WidgetPicker.kt from either module root or project root (handles both AGP layouts). */
  private val widgetPickerContent: String by lazy {
    val modulePath = "src/main/kotlin/app/dqxn/android/feature/settings/WidgetPicker.kt"
    val projectPath =
      "feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/WidgetPicker.kt"
    val dir = File(System.getProperty("user.dir")!!)
    val moduleFile = File(dir, modulePath)
    val projectFile = File(dir, projectPath)
    when {
      moduleFile.exists() -> moduleFile.readText()
      projectFile.exists() -> projectFile.readText()
      else ->
        error(
          "WidgetPicker.kt not found at $moduleFile or $projectFile (user.dir=${dir.absolutePath})"
        )
    }
  }

  @Test
  fun `uses LazyVerticalStaggeredGrid not FlowRow`() {
    assertThat(widgetPickerContent).contains("LazyVerticalStaggeredGrid")
    assertThat(widgetPickerContent).doesNotContain("FlowRow")
    assertThat(widgetPickerContent).contains("StaggeredGridCells.Adaptive")
    assertThat(widgetPickerContent).contains("120.dp")
  }

  @Test
  fun `wide widgets span full line`() {
    assertThat(widgetPickerContent).contains("StaggeredGridItemSpan.FullLine")
    assertThat(widgetPickerContent).contains("1.5f")
  }

  @Test
  fun `pack headers span full line`() {
    // Pack header uses FullLine span
    assertThat(widgetPickerContent).contains("header_")
    assertThat(widgetPickerContent).contains("StaggeredGridItemSpan.FullLine")
  }

  @Test
  fun `no hardcoded PREVIEW_ASPECT_RATIO constant`() {
    assertThat(widgetPickerContent).doesNotContain("PREVIEW_ASPECT_RATIO")
  }

  @Test
  fun `widget sort order is compact first then priority then name`() {
    assertThat(widgetPickerContent).contains("sortedWith")
    assertThat(widgetPickerContent).contains("priority")
    assertThat(widgetPickerContent).contains("displayName")
  }

  @Test
  fun `preserves lock icon and hardware badge functionality`() {
    assertThat(widgetPickerContent).contains("widget_lock_")
    assertThat(widgetPickerContent).contains("widget_hw_")
    assertThat(widgetPickerContent).contains("HardwareRequirement")
  }

  @Test
  fun `no scrollable Column wrapper around grid`() {
    // verticalScroll should not be used (grid handles its own scrolling)
    assertThat(widgetPickerContent).doesNotContain("verticalScroll")
    assertThat(widgetPickerContent).doesNotContain("rememberScrollState")
  }
}
