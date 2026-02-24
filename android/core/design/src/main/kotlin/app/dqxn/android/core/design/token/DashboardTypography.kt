package app.dqxn.android.core.design.token

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Named text styles for dashboard UI roles.
 *
 * Ported verbatim from old codebase `DashboardThemeExtensions.kt`. Each property references
 * [MaterialTheme.typography] with optional size/weight overrides.
 *
 * ## Titles & Headers
 * - [title] -- Sheet titles, browser/picker card titles (titleMedium, 17.sp)
 * - [sectionHeader] -- Section dividers like STATUS, CONFIGURATION (labelMedium, 13.sp)
 *
 * ## Content (3 tiers by prominence)
 * - [itemTitle] -- Settings row titles, primary list items (titleMedium)
 * - [label] -- Field labels, compact card titles (labelLarge)
 * - [description] -- Secondary text, subtitles, helper text (bodyMedium)
 *
 * ## Actions
 * - [buttonLabel] -- Buttons, chips, status labels (labelMedium, 13.sp, Bold)
 * - [primaryButtonLabel] -- Primary navigation actions like Next, Done (labelLarge, 15.sp, Bold)
 *
 * ## Meta
 * - [caption] -- Tags, badges, stats, metadata (labelSmall)
 */
public object DashboardTypography {

  // Titles & Headers

  /** Sheet/overlay titles, browser card titles, picker section headers. */
  public val title: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp)

  /** Section dividers (STATUS, CONFIGURATION, etc.). */
  public val sectionHeader: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)

  // Content

  /** Primary item titles in settings rows and list items. */
  public val itemTitle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium

  /** Field labels, compact card titles. Add fontWeight at call site if needed. */
  public val label: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge

  /** Descriptions, subtitles, helper text. */
  public val description: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium

  // Actions

  /** Button and chip labels, status indicators. */
  public val buttonLabel: TextStyle
    @Composable get() =
      MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)

  /** Primary action buttons (Next, Done, navigation buttons). */
  public val primaryButtonLabel: TextStyle
    @Composable get() =
      MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)

  // Meta

  /** Tags, badges, stats, captions, metadata. */
  public val caption: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmall

  /**
   * Derives a tighter text style from [base] by applying [lineHeightMultiplier] to the font size.
   * Useful for dense widget numerics and status labels.
   */
  public fun getTightTextStyle(base: TextStyle, lineHeightMultiplier: Float = 1.1f): TextStyle =
    base.copy(lineHeight = base.fontSize * lineHeightMultiplier)
}
