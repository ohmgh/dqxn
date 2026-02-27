package app.dqxn.android.pack.essentials.widgets.shortcuts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.InfoCardLayoutMode
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.settings.SizeOption
import app.dqxn.android.sdk.contracts.settings.infoCardSettingsSchema
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.layout.InfoCardLayout
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

/**
 * App launcher shortcut widget.
 *
 * Displays an app icon via [InfoCardLayout] with the app's display name. Tap handling delegates to
 * `CallActionProvider` via the binder. No data snapshot required -- this is an action-only widget.
 */
@DashboardWidget(typeId = "essentials:shortcuts", displayName = "Shortcuts")
public class ShortcutsRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:shortcuts"
  override val displayName: String = "Shortcuts"
  override val description: String = "Launch an app with a single tap"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = emptySet()
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = true
  override val priority: Int = 50
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.AppPickerSetting(
        key = "packageName",
        label = "App",
        description = "Select the app to launch",
        default = null,
        suggestedPackages =
          listOf(
            "com.google.android.apps.maps",
            "com.spotify.music",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.google.android.youtube.music",
          ),
      ),
      SettingDefinition.StringSetting(
        key = "displayName",
        label = "Display Name",
        description = "Custom name (uses app name if empty)",
        default = "",
        maxLength = 30,
      ),
    ) + infoCardSettingsSchema()

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 9, heightUnits = 9, aspectRatio = null, settings = emptyMap())

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    @Suppress("UNUSED_VARIABLE") val widgetData = LocalWidgetData.current
    val packageName = settings["packageName"] as? String
    val customDisplayName = settings["displayName"] as? String ?: ""
    val layoutMode =
      (settings["info_card_layout_mode"] as? InfoCardLayoutMode) ?: InfoCardLayoutMode.STANDARD
    val sizeOption = (settings["info_card_size"] as? SizeOption) ?: SizeOption.MEDIUM
    val appContext = LocalContext.current

    val appLabel =
      remember(packageName) {
        if (packageName != null) {
          try {
            val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(appInfo).toString()
          } catch (_: Exception) {
            packageName.substringAfterLast('.')
          }
        } else {
          null
        }
      }

    val resolvedName =
      if (customDisplayName.isNotBlank()) customDisplayName else appLabel ?: "Tap to configure"

    InfoCardLayout(
      modifier = modifier,
      layoutMode = layoutMode,
      iconSize = sizeOption,
      topTextSize = sizeOption,
      icon = { dp: Dp -> ShortcutIcon(packageName = packageName, size = dp) },
      topText = { textStyle: TextStyle ->
        Text(
          text = resolvedName,
          style = textStyle,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      },
    )
  }

  override fun accessibilityDescription(data: WidgetData): String {
    // Shortcuts is action-only (no snapshot data). Differentiate empty (unconfigured) vs active
    // (bound) state via timestamp. When the binder assigns this widget, timestamp > 0.
    return if (data.timestamp > 0L) {
      "Shortcut: ready"
    } else {
      "Shortcut: tap to configure"
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean {
    // Tap is handled by CallActionProvider via the binder routing.
    // Return true to indicate this widget handles taps.
    val packageName = settings["packageName"] as? String
    return packageName != null
  }
}

@Composable
private fun ShortcutIcon(packageName: String?, size: Dp) {
  if (packageName != null) {
    val appContext = LocalContext.current
    val appIcon: Drawable? =
      remember(packageName) {
        try {
          val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
          appContext.packageManager.getApplicationIcon(appInfo)
        } catch (_: Exception) {
          null
        }
      }
    if (appIcon != null) {
      val imageBitmap =
        remember(appIcon) {
          val bmp =
            Bitmap.createBitmap(
              appIcon.intrinsicWidth.coerceAtLeast(1),
              appIcon.intrinsicHeight.coerceAtLeast(1),
              Bitmap.Config.ARGB_8888,
            )
          val canvas = Canvas(bmp)
          appIcon.setBounds(0, 0, canvas.width, canvas.height)
          appIcon.draw(canvas)
          bmp.asImageBitmap()
        }
      Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = Modifier.size(size),
      )
    } else {
      PlaceholderIcon(size)
    }
  } else {
    PlaceholderIcon(size)
  }
}

/** Simple rounded-rect placeholder icon drawn via Canvas when no app is selected. */
@Composable
private fun PlaceholderIcon(size: Dp) {
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  Canvas(modifier = Modifier.size(size)) {
    val strokeWidth = 2.dp.toPx()
    val inset = strokeWidth / 2f
    drawRoundRect(
      color = color,
      topLeft = Offset(inset, inset),
      size = Size(this.size.width - strokeWidth, this.size.height - strokeWidth),
      cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
      style = Stroke(width = strokeWidth),
    )
    // Draw a simple "+" in the center
    val centerX = this.size.width / 2f
    val centerY = this.size.height / 2f
    val armLength = this.size.width * 0.2f
    drawLine(
      color = color,
      start = Offset(centerX - armLength, centerY),
      end = Offset(centerX + armLength, centerY),
      strokeWidth = strokeWidth,
    )
    drawLine(
      color = color,
      start = Offset(centerX, centerY - armLength),
      end = Offset(centerX, centerY + armLength),
      strokeWidth = strokeWidth,
    )
  }
}
