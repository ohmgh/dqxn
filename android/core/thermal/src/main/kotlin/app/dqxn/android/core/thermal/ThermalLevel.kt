package app.dqxn.android.core.thermal

/**
 * Thermal severity levels mapped from [android.os.PowerManager] thermal status constants.
 * - [NORMAL]: Full rendering capabilities (60 fps, glow enabled).
 * - [WARM]: Slight throttling (45 fps), glow still enabled.
 * - [DEGRADED]: Significant throttling (30 fps), glow replaced with gradient fallback.
 * - [CRITICAL]: Emergency throttling (24 fps), glow disabled entirely.
 */
public enum class ThermalLevel {
  NORMAL,
  WARM,
  DEGRADED,
  CRITICAL,
}
