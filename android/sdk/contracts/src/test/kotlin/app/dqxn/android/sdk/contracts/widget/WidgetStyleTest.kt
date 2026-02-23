package app.dqxn.android.sdk.contracts.widget

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class WidgetStyleTest {

  @Test
  fun `default values match documented`() {
    val style = WidgetStyle.Default

    assertThat(style.backgroundStyle).isEqualTo(BackgroundStyle.NONE)
    assertThat(style.opacity).isEqualTo(1.0f)
    assertThat(style.showBorder).isFalse()
    assertThat(style.hasGlowEffect).isFalse()
    assertThat(style.cornerRadiusPercent).isEqualTo(25)
    assertThat(style.rimSizePercent).isEqualTo(0)
    assertThat(style.zLayer).isEqualTo(0)
  }

  @Test
  fun `serialization round-trip`() {
    val original = WidgetStyle.Default
    val json = Json.encodeToString(WidgetStyle.serializer(), original)
    val restored = Json.decodeFromString(WidgetStyle.serializer(), json)

    assertThat(restored).isEqualTo(original)
  }
}
