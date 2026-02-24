package app.dqxn.android.core.thermal

/**
 * Rendering configuration derived from [ThermalLevel]. Consumed by the dashboard layer to adjust
 * visual fidelity based on thermal state.
 *
 * @property targetFps Target frame rate for the dashboard canvas.
 * @property glowEnabled Whether glow effects (blur-based) should be rendered.
 * @property useGradientFallback Whether to use RadialGradient fallback instead of RenderEffect
 *   glow.
 */
public data class RenderConfig(
  val targetFps: Float,
  val glowEnabled: Boolean,
  val useGradientFallback: Boolean,
) {
  public companion object {
    public val DEFAULT: RenderConfig =
      RenderConfig(targetFps = 60f, glowEnabled = true, useGradientFallback = false)
  }
}
