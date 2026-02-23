package app.dqxn.android.sdk.contracts.widget

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.Locale
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class WidgetContextTest {

  @Test
  fun `DEFAULT is UTC, US locale, US region`() {
    val ctx = WidgetContext.DEFAULT

    assertThat(ctx.timezone).isEqualTo(ZoneId.of("UTC"))
    assertThat(ctx.locale).isEqualTo(Locale.US)
    assertThat(ctx.region).isEqualTo("US")
  }
}
