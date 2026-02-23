package app.dqxn.android.sdk.contracts.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

/**
 * Minimal test stub implementing [WidgetRenderer] for contract test validation.
 *
 * All properties are constructor-configurable. The [Render] function is a no-op — no Compose
 * compiler in `:sdk:contracts`, so no real Compose body.
 */
public class TestWidgetRenderer(
  override val typeId: String = "test:widget",
  override val displayName: String = "Test Widget",
  override val description: String = "A test widget for contract validation",
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = emptySet(),
  override val settingsSchema: List<SettingDefinition<*>> = emptyList(),
  override val aspectRatio: Float? = null,
  override val supportsTap: Boolean = false,
  override val priority: Int = 0,
  override val requiredAnyEntitlement: Set<String>? = null,
) : WidgetRenderer {

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(
      widthUnits = 6,
      heightUnits = 6,
      aspectRatio = aspectRatio,
      settings = emptyMap(),
    )

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    // No-op: no Compose compiler in :sdk:contracts, so no real composable body.
    // Packs (which have the Compose compiler) provide real Render() implementations.
  }

  override fun accessibilityDescription(data: WidgetData): String =
    if (data.hasData()) "Test widget with data" else "Test widget: no data"

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false
}
