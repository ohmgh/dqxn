package app.dqxn.android.app.legal

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * Verifies the Terms of Service speed accuracy disclaimer string resource (NF-D2).
 *
 * Parses `app/src/main/res/values/strings.xml` directly as XML and asserts the
 * `tos_speed_disclaimer` element exists with required legal phrases. This ensures the disclaimer
 * text cannot be accidentally emptied or stripped without breaking the build.
 *
 * Pure JVM test -- no Robolectric or Android runtime required.
 */
class ToSDisclaimerTest {

  private val stringsFile: File by lazy {
    // Gradle sets user.dir to the module root (android/app)
    val moduleDir = File(checkNotNull(System.getProperty("user.dir")) { "user.dir not set" })
    val file = File(moduleDir, "src/main/res/values/strings.xml")
    assertThat(file.exists()).isTrue()
    file
  }

  private val disclaimerText: String by lazy {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val document = builder.parse(stringsFile)
    document.documentElement.normalize()

    val stringElements: NodeList = document.getElementsByTagName("string")
    var text: String? = null
    for (i in 0 until stringElements.length) {
      val element = stringElements.item(i) as Element
      if (element.getAttribute("name") == "tos_speed_disclaimer") {
        text = element.textContent
        break
      }
    }

    assertWithMessage("tos_speed_disclaimer string resource must exist").that(text).isNotNull()
    text!!
  }

  @Test
  fun disclaimerElementExists() {
    // Accessing disclaimerText triggers the assertion that the element exists
    assertThat(disclaimerText).isNotEmpty()
  }

  @Test
  fun disclaimerContainsApproximate() {
    assertThat(disclaimerText.lowercase()).contains("approximate")
  }

  @Test
  fun disclaimerContainsNotCertifiedSpeedometer() {
    assertThat(disclaimerText.lowercase()).containsMatch("not.*certified.*speedometer")
  }

  @Test
  fun disclaimerContainsDisclaimerLanguage() {
    assertThat(disclaimerText.lowercase()).containsMatch("disclaim(s|er)")
  }

  @Test
  fun disclaimerContainsLiability() {
    assertThat(disclaimerText.lowercase()).contains("liability")
  }

  @Test
  fun disclaimerContainsSpeed() {
    assertThat(disclaimerText.lowercase()).contains("speed")
  }
}
