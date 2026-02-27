package app.dqxn.android

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

class SplashThemeTest {

  private val projectDir = File(checkNotNull(System.getProperty("user.dir")))

  @Test
  fun `values themes xml contains Theme App Starting with splash background`() {
    val themesXml = File(projectDir, "src/main/res/values/themes.xml")
    assertThat(themesXml.exists()).isTrue()
    val content = themesXml.readText()
    assertThat(content).contains("Theme.App.Starting")
    assertThat(content).contains("windowSplashScreenBackground")
    assertThat(content).contains("#0f172a")
    assertThat(content).contains("postSplashScreenTheme")
    assertThat(content).contains("Theme.Dqxn.NoActionBar")
    assertThat(content).contains("ic_logo_letterform")
  }

  @Test
  fun `values-v31 themes xml contains native splash attributes`() {
    val themesV31 = File(projectDir, "src/main/res/values-v31/themes.xml")
    assertThat(themesV31.exists()).isTrue()
    val content = themesV31.readText()
    assertThat(content).contains("android:windowSplashScreenBackground")
    assertThat(content).contains("android:windowSplashScreenAnimatedIcon")
    assertThat(content).contains("#0f172a")
  }

  @Test
  fun `manifest references splash theme on activity`() {
    val manifest = File(projectDir, "src/main/AndroidManifest.xml")
    assertThat(manifest.exists()).isTrue()
    val content = manifest.readText()
    assertThat(content).contains("@style/Theme.App.Starting")
  }
}
