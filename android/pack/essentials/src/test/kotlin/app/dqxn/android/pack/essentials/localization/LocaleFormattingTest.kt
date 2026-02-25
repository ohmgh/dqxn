package app.dqxn.android.pack.essentials.localization

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Verifies essentials pack renderers use locale-aware formatting for numbers and dates.
 *
 * Two-pronged approach:
 * 1. **Source parsing**: Scans renderer files for locale-aware API usage patterns (NumberFormat,
 *    DateTimeFormatter with Locale, etc.) and flags non-locale-aware patterns.
 * 2. **Runtime verification**: Calls NumberFormat with different Locales to confirm output changes.
 */
@Tag("fast")
class LocaleFormattingTest {

  private fun findWidgetsDir(): File {
    var dir = File(System.getProperty("user.dir"))
    while (dir.parentFile != null) {
      val candidate =
        File(
          dir,
          "pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets",
        )
      if (candidate.isDirectory) return candidate
      dir = dir.parentFile
    }
    error("Could not find essentials pack widgets directory")
  }

  @Test
  fun `speedometer renderer uses locale-aware number formatting`() {
    val widgetsDir = findWidgetsDir()
    val file = File(widgetsDir, "speedometer/SpeedometerRenderer.kt")
    assertWithMessage("SpeedometerRenderer.kt must exist").that(file.exists()).isTrue()

    val content = file.readText()

    assertWithMessage("SpeedometerRenderer must use NumberFormat.getInstance(Locale)")
      .that(content)
      .contains("NumberFormat.getInstance(Locale.getDefault())")

    // Verify no raw toString() on speed values used for display
    assertWithMessage("SpeedometerRenderer should not use raw format without Locale for speed text")
      .that(content)
      .doesNotContain("String.format(\"%")
  }

  @Test
  fun `battery renderer uses locale-aware number formatting`() {
    val widgetsDir = findWidgetsDir()
    val file = File(widgetsDir, "battery/BatteryRenderer.kt")
    assertWithMessage("BatteryRenderer.kt must exist").that(file.exists()).isTrue()

    val content = file.readText()

    assertWithMessage("BatteryRenderer must use NumberFormat.getInstance(Locale)")
      .that(content)
      .contains("NumberFormat.getInstance(Locale.getDefault())")
  }

  @Test
  fun `clock digital renderer uses locale-aware date formatting`() {
    val widgetsDir = findWidgetsDir()
    val file = File(widgetsDir, "clock/ClockDigitalRenderer.kt")
    assertWithMessage("ClockDigitalRenderer.kt must exist").that(file.exists()).isTrue()

    val content = file.readText()

    assertWithMessage("ClockDigitalRenderer must use DateTimeFormatter with Locale")
      .that(content)
      .contains("DateTimeFormatter.ofPattern(pattern, Locale.getDefault())")

    // Verify no SimpleDateFormat without Locale
    assertWithMessage("ClockDigitalRenderer should not use SimpleDateFormat")
      .that(content)
      .doesNotContain("SimpleDateFormat")
  }

  @Test
  fun `date simple renderer uses locale-aware date formatting`() {
    val widgetsDir = findWidgetsDir()
    val file = File(widgetsDir, "date/DateSimpleRenderer.kt")
    assertWithMessage("DateSimpleRenderer.kt must exist").that(file.exists()).isTrue()

    val content = file.readText()

    assertWithMessage("DateSimpleRenderer must use DateTimeFormatter with Locale")
      .that(content)
      .contains("DateTimeFormatter.ofPattern(formatOption.pattern, Locale.getDefault())")

    assertWithMessage("DateSimpleRenderer should not use SimpleDateFormat")
      .that(content)
      .doesNotContain("SimpleDateFormat")
  }

  @Test
  fun `ambient light renderer uses locale-aware number formatting`() {
    val widgetsDir = findWidgetsDir()
    val file = File(widgetsDir, "ambientlight/AmbientLightRenderer.kt")
    assertWithMessage("AmbientLightRenderer.kt must exist").that(file.exists()).isTrue()

    val content = file.readText()

    assertWithMessage("AmbientLightRenderer must use NumberFormat.getInstance(Locale)")
      .that(content)
      .contains("NumberFormat.getInstance(Locale.getDefault())")
  }

  @Test
  fun `solar renderer uses locale-aware date formatting`() {
    val widgetsDir = findWidgetsDir()
    val file = File(widgetsDir, "solar/SolarRenderer.kt")
    assertWithMessage("SolarRenderer.kt must exist").that(file.exists()).isTrue()

    val content = file.readText()

    // SolarRenderer uses SimpleDateFormat with Locale -- acceptable for legacy compatibility
    assertWithMessage("SolarRenderer must pass Locale to SimpleDateFormat")
      .that(content)
      .contains("Locale.getDefault()")
  }

  @Test
  fun `no renderer uses non-locale String format for numeric display`() {
    val widgetsDir = findWidgetsDir()
    val violations = mutableListOf<String>()

    widgetsDir.walkTopDown().filter { it.name.endsWith("Renderer.kt") }.forEach { file ->
      val lines = file.readLines()
      lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

        // Flag non-locale String.format with numeric format specifiers
        // (e.g., String.format("%.1f", value) without Locale parameter)
        if (NON_LOCALE_FORMAT.containsMatchIn(line)) {
          // Allow digit-only format patterns like "%02d" for hour/minute padding
          // These produce identical output across all locales
          val match = NON_LOCALE_FORMAT.find(line)!!.value
          if (!DIGIT_ONLY_FORMAT.containsMatchIn(match)) {
            val relativePath = file.name
            violations.add("$relativePath:${index + 1}: $trimmed")
          }
        }
      }
    }

    assertWithMessage(
        "Renderers should not use non-locale String.format for numeric display.\n" +
          "Violations:\n${violations.joinToString("\n")}"
      )
      .that(violations)
      .isEmpty()
  }

  @Test
  fun `NumberFormat produces different output for different Locales`() {
    // Runtime verification: NumberFormat respects Locale for decimal formatting
    val usFormat = NumberFormat.getInstance(Locale.US)
    val deFormat = NumberFormat.getInstance(Locale.GERMANY)

    val value = 1234.5

    val usResult = usFormat.format(value)
    val deResult = deFormat.format(value)

    // US uses comma for thousands + period for decimal: "1,234.5"
    // Germany uses period for thousands + comma for decimal: "1.234,5"
    assertWithMessage("US locale should use period as decimal separator")
      .that(usResult)
      .contains(".")

    assertWithMessage("German locale should use comma as decimal separator")
      .that(deResult)
      .contains(",")

    assertWithMessage("Different locales should produce different formatted output")
      .that(usResult)
      .isNotEqualTo(deResult)
  }

  companion object {
    /**
     * Matches String.format("%" ...) without a Locale first argument. This regex looks for
     * String.format or .format( with a format string starting with % but NOT preceded by Locale.
     */
    private val NON_LOCALE_FORMAT =
      Regex("""String\.format\(\s*"(%[^"]*)"[^)]*\)|"(%[^"]*)"\.format\(""")

    /** Matches digit-only format patterns like %02d, %d -- locale-independent. */
    private val DIGIT_ONLY_FORMAT = Regex("""%\d*d""")
  }
}
