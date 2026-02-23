package app.dqxn.android.sdk.contracts.widget

import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import kotlin.reflect.KClass

public interface WidgetSpec {
  public val typeId: String
  public val displayName: String
  public val description: String
  public val compatibleSnapshots: Set<KClass<out DataSnapshot>>
  public val settingsSchema: List<SettingDefinition<*>>
  public val aspectRatio: Float?
  public val supportsTap: Boolean
  public val priority: Int

  public fun getDefaults(context: WidgetContext): WidgetDefaults
}
