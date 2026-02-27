package app.dqxn.android.app.integration

import app.dqxn.android.sdk.contracts.pack.DashboardPackManifest
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hilt integration test that validates all 3 packs (essentials, themes, demo) contribute to Hilt
 * multibinding sets correctly when loaded simultaneously.
 *
 * Uses Robolectric for JVM-based Hilt validation -- no device required. Verifies:
 * - All widget renderers resolve without binding conflicts
 * - All data providers resolve without binding conflicts
 * - All theme providers resolve without binding conflicts
 * - All pack manifests are present for the 3 packs
 * - No duplicate typeIds across widget renderers
 * - No duplicate sourceIds across data providers
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MultiPackHiltTest {

  @get:Rule val hiltRule = HiltAndroidRule(this)

  @Inject lateinit var widgetRenderers: Set<@JvmSuppressWildcards WidgetRenderer>

  @Inject lateinit var dataProviders: Set<@JvmSuppressWildcards DataProvider<*>>

  @Inject lateinit var themeProviders: Set<@JvmSuppressWildcards ThemeProvider>

  @Inject lateinit var packManifests: Set<@JvmSuppressWildcards DashboardPackManifest>

  @Before
  fun setup() {
    hiltRule.inject()
  }

  @Test
  fun `widget renderers from all packs resolve`() {
    // Essentials pack contributes 13 widgets; no widgets from themes or demo
    assertThat(widgetRenderers).isNotEmpty()
    assertThat(widgetRenderers.size).isAtLeast(13)
  }

  @Test
  fun `data providers from all packs resolve`() {
    // Essentials: 9 providers, Demo: 8 providers = 17+ total
    assertThat(dataProviders).isNotEmpty()
    assertThat(dataProviders.size).isAtLeast(17)
  }

  @Test
  fun `theme providers from all packs resolve`() {
    // Essentials: 1, Themes: 1 = 2+ total
    assertThat(themeProviders).isNotEmpty()
    assertThat(themeProviders.size).isAtLeast(2)
  }

  @Test
  fun `pack manifests contain all 3 packs`() {
    val packIds = packManifests.map { it.packId }.toSet()
    assertThat(packIds).containsExactly("essentials", "themes", "demo")
  }

  @Test
  fun `no duplicate widget typeIds`() {
    val typeIds = widgetRenderers.map { it.typeId }
    assertThat(typeIds).containsNoDuplicates()
  }

  @Test
  fun `no duplicate provider sourceIds`() {
    val sourceIds = dataProviders.map { it.sourceId }
    assertThat(sourceIds).containsNoDuplicates()
  }
}
