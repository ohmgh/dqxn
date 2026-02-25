package app.dqxn.android.core.design.token

import androidx.compose.ui.graphics.Color

/**
 * Static semantic color tokens for informational state indicators (info rows, setup cards, status
 * badges). These are NOT per-theme colors -- they are fixed for consistent meaning regardless of
 * the active dashboard theme.
 */
public object SemanticColors {

  /** Informational state -- Material Blue 500. */
  public val Info: Color = Color(0xFF2196F3)

  /** Warning state -- Material Orange 400. */
  public val Warning: Color = Color(0xFFFFA726)

  /** Success state -- Material Green 400. */
  public val Success: Color = Color(0xFF66BB6A)

  /** Error state -- Material Red 400. */
  public val Error: Color = Color(0xFFEF5350)
}
