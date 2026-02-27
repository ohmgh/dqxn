package app.dqxn.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.pack.DashboardPackManifest
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/**
 * Hardware requirement derived from
 * [WidgetSpec.compatibleSnapshots][app.dqxn.android.sdk.contracts.widget.WidgetSpec.compatibleSnapshots]
 * class names. Used to render small GPS/BLE icon badges in the widget picker.
 */
private enum class HardwareRequirement {
  GPS,
  BLUETOOTH,
  NONE,
}

/**
 * Derive hardware requirement from snapshot class names.
 *
 * Mapping:
 * - SpeedSnapshot / SolarSnapshot (not timezone) -> GPS
 * - BleSnapshot / BluetoothSnapshot -> BLUETOOTH
 * - Everything else (Time, Battery, AmbientLight, Acceleration, Orientation) -> NONE
 */
private fun deriveHardwareRequirement(
  compatibleSnapshots: Set<KClass<out DataSnapshot>>,
): HardwareRequirement {
  val names = compatibleSnapshots.mapNotNull { it.simpleName }
  return when {
    names.any { name ->
      name.contains("Speed", ignoreCase = true) ||
        (name.contains("Solar", ignoreCase = true) && !name.contains("Timezone", ignoreCase = true))
    } -> HardwareRequirement.GPS
    names.any { name ->
      name.contains("Ble", ignoreCase = true) || name.contains("Bluetooth", ignoreCase = true)
    } -> HardwareRequirement.BLUETOOTH
    else -> HardwareRequirement.NONE
  }
}

/**
 * Resolve pack display name from [DashboardPackManifest] set, falling back to capitalized packId.
 */
private fun resolvePackName(
  packId: String,
  manifests: Set<DashboardPackManifest>,
): String {
  return manifests.firstOrNull { it.packId == packId }?.displayName
    ?: packId.replaceFirstChar { it.uppercase() }
}

/**
 * Widget selection grid with **live previews** (F2.7).
 *
 * Widgets grouped by pack using [LazyVerticalStaggeredGrid] with adaptive columns. Each widget card
 * contains a scaled-down live preview using [WidgetRenderer.Render] fed by demo data. Entitlement
 * badges (lock icon) render on gated widgets per F8.7. All widgets shown regardless of entitlement
 * (preview-regardless-of-entitlement). Adding a gated widget checks accessibility -- if not
 * accessible, gate at persistence.
 *
 * Wide widgets (aspectRatio > 1.5) span the full line. Pack headers also span full line. Widgets
 * sorted: compact first, then by priority descending, then alphabetically.
 *
 * Uses [OverlayScaffold] with [OverlayType.Hub] (full-screen). The staggered grid handles its own
 * scrolling -- no wrapping scrollable Column.
 */
@Composable
public fun WidgetPicker(
  widgetRegistry: WidgetRegistry,
  entitlementManager: EntitlementManager,
  packManifests: Set<DashboardPackManifest> = emptySet(),
  onSelectWidget: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  onRevocationToast: ((String) -> Unit)? = null,
) {
  val theme = LocalDashboardTheme.current
  val allWidgets = remember(widgetRegistry) { widgetRegistry.getAll() }

  // Group and sort widgets by pack: compact first, then priority desc, then alphabetically
  val sortedWidgetsByPack: ImmutableMap<String, List<WidgetRenderer>> =
    remember(allWidgets) {
      allWidgets
        .groupBy { it.typeId.substringBefore(':') }
        .mapValues { (_, widgets) ->
          widgets.sortedWith(
            compareBy<WidgetRenderer> { (it.aspectRatio ?: 1f) > 1.5f }
              .thenByDescending { it.priority }
              .thenBy { it.displayName }
          )
        }
        .toImmutableMap()
    }

  // F8.9: Entitlement revocation toast -- shown once per composition
  var revocationToastShown by remember { mutableStateOf(false) }
  LaunchedEffect(allWidgets, entitlementManager) {
    if (!revocationToastShown) {
      val hasRevoked =
        allWidgets.any { widget ->
          val required = widget.requiredAnyEntitlement
          !required.isNullOrEmpty() && !widget.isAccessible(entitlementManager::hasEntitlement)
        }
      if (hasRevoked) {
        revocationToastShown = true
        onRevocationToast?.invoke(
          "Some features are no longer available. Your layout is preserved."
        )
      }
    }
  }

  OverlayScaffold(
    title = stringResource(R.string.widget_picker_title),
    overlayType = OverlayType.Hub,
    onClose = onDismiss,
    modifier = modifier.fillMaxSize(),
  ) {
    LazyVerticalStaggeredGrid(
      columns = StaggeredGridCells.Adaptive(minSize = 120.dp),
      contentPadding = PaddingValues(vertical = DashboardSpacing.ItemGap),
      horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
      verticalItemSpacing = DashboardSpacing.ItemGap,
      modifier = Modifier.fillMaxSize().testTag("widget_picker_grid"),
    ) {
      sortedWidgetsByPack.forEach { (packId, widgets) ->
        // Pack header -- always spans full line
        item(
          key = "header_$packId",
          span = StaggeredGridItemSpan.FullLine,
        ) {
          Text(
            text = resolvePackName(packId, packManifests),
            style = DashboardTypography.sectionHeader,
            color = theme.secondaryTextColor,
            modifier =
              Modifier.padding(vertical = DashboardSpacing.InGroupGap)
                .testTag("pack_header_$packId"),
          )
        }

        // Widget cards -- wide widgets span full line
        items(
          items = widgets,
          key = { it.typeId },
          span = { widget ->
            if ((widget.aspectRatio ?: 1f) > 1.5f) {
              StaggeredGridItemSpan.FullLine
            } else {
              StaggeredGridItemSpan.SingleLane
            }
          },
        ) { widget ->
          WidgetPickerCard(
            widget = widget,
            entitlementManager = entitlementManager,
            theme = theme,
            onSelect = { typeId ->
              if (widget.isAccessible(entitlementManager::hasEntitlement)) {
                onSelectWidget(typeId)
              }
            },
          )
        }
      }
    }
  }
}

/**
 * Widget card with live preview, display name, and entitlement badge.
 *
 * Minimum touch target 76dp (F10.4). Preview uses aspect ratio from [WidgetRenderer.aspectRatio]
 * (defaulting to 1:1) and background brush from theme. The [aspectRatio] modifier is safe here
 * because we are inside [LazyVerticalStaggeredGrid] (bounded constraints), NOT inside a scrollable
 * Column (which gave unbounded height).
 */
@Composable
private fun WidgetPickerCard(
  widget: WidgetRenderer,
  entitlementManager: EntitlementManager,
  theme: DashboardThemeDefinition,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isAccessible = widget.isAccessible(entitlementManager::hasEntitlement)
  val shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius)
  val widgetAspectRatio = widget.aspectRatio ?: 1f

  Column(
    modifier =
      modifier
        .sizeIn(minHeight = 76.dp)
        .clip(shape)
        .border(1.dp, theme.widgetBorderColor.copy(alpha = 0.3f), shape)
        .clickable { onSelect(widget.typeId) }
        .padding(DashboardSpacing.InGroupGap)
        .testTag("widget_card_${widget.typeId}"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Live preview area -- uses aspectRatio modifier now that we are inside
    // LazyVerticalStaggeredGrid (bounded constraints per cell)
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(widgetAspectRatio.coerceIn(0.5f, 3f))
          .clip(RoundedCornerShape(CardSize.SMALL.cornerRadius))
          .background(theme.widgetBackgroundBrush, RoundedCornerShape(CardSize.SMALL.cornerRadius))
          .clipToBounds()
          .testTag("widget_preview_${widget.typeId}"),
      contentAlignment = Alignment.Center,
    ) {
      // Live widget preview -- scale computed to fit the preview box
      CompositionLocalProvider(LocalWidgetData provides WidgetData.Empty) {
        Box(
          modifier =
            Modifier.graphicsLayer {
              scaleX = PREVIEW_SCALE
              scaleY = PREVIEW_SCALE
            },
          contentAlignment = Alignment.Center,
        ) {
          widget.Render(
            isEditMode = false,
            style = WidgetStyle.Default,
            settings = persistentMapOf(),
            modifier = Modifier,
          )
        }
      }

      // Lock icon overlay for gated widgets (centered, on top of preview)
      if (!isAccessible) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = stringResource(R.string.widget_picker_locked),
          tint = theme.secondaryTextColor.copy(alpha = DashboardThemeDefinition.EMPHASIS_MEDIUM),
          modifier = Modifier.testTag("widget_lock_${widget.typeId}"),
        )
      }

      // Hardware requirement icon badge (bottom-end corner)
      val hwReq =
        remember(widget.compatibleSnapshots) {
          deriveHardwareRequirement(widget.compatibleSnapshots)
        }
      when (hwReq) {
        HardwareRequirement.GPS,
        HardwareRequirement.BLUETOOTH -> {
          Icon(
            imageVector =
              when (hwReq) {
                HardwareRequirement.GPS -> Icons.Filled.LocationOn
                HardwareRequirement.BLUETOOTH -> Icons.Filled.Bluetooth
                HardwareRequirement.NONE -> return@Box // unreachable
              },
            contentDescription =
              when (hwReq) {
                HardwareRequirement.GPS -> stringResource(R.string.widget_picker_requires_gps)
                HardwareRequirement.BLUETOOTH ->
                  stringResource(R.string.widget_picker_requires_bluetooth)
                HardwareRequirement.NONE -> null // unreachable
              },
            tint = theme.secondaryTextColor.copy(alpha = 0.8f),
            modifier =
              Modifier.align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(16.dp)
                .testTag("widget_hw_${widget.typeId}"),
          )
        }
        HardwareRequirement.NONE -> {
          /* no badge */
        }
      }
    }

    // Widget display name
    Text(
      text = widget.displayName,
      style = DashboardTypography.label,
      color = theme.primaryTextColor,
      modifier =
        Modifier.padding(top = DashboardSpacing.InGroupGap).testTag("widget_name_${widget.typeId}"),
      maxLines = 1,
    )

    // One-line description
    widget.description
      .takeIf { it.isNotBlank() }
      ?.let { desc ->
        Text(
          text = desc,
          style = DashboardTypography.caption,
          color = theme.secondaryTextColor,
          maxLines = 1,
        )
      }
  }
}

/** Preview scale for widget cards in the picker grid. */
private const val PREVIEW_SCALE = 0.45f
