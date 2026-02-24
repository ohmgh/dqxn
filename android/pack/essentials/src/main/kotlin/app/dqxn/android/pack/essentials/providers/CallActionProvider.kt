package app.dqxn.android.pack.essentials.providers

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.ActionableProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.provider.UnitSnapshot
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Action provider for the Shortcuts widget. Implements [ActionableProvider] to handle tap-to-launch
 * actions. Emits a single [UnitSnapshot] -- exists to receive [WidgetAction] events.
 */
@DashboardDataProvider(
  localId = "call-action",
  displayName = "App Launcher",
  description = "Launch an app from a Shortcuts widget tap",
)
@Singleton
class CallActionProvider
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
) : ActionableProvider<UnitSnapshot> {

  override val snapshotType: KClass<UnitSnapshot> = UnitSnapshot::class
  override val sourceId: String = "essentials:call-action"
  override val displayName: String = "App Launcher"
  override val description: String = "Launch an app from a Shortcuts widget tap"
  override val dataType: String = DataTypes.SPEED
  override val priority: ProviderPriority = ProviderPriority.SIMULATED
  override val subscriberTimeout: Duration = 5.seconds
  override val firstEmissionTimeout: Duration = 5.seconds
  override val isAvailable: Boolean = true
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields = listOf(DataFieldSpec(name = "unit", typeId = "Unit")),
      stalenessThresholdMs = Long.MAX_VALUE,
    )

  override fun provideState(): Flow<UnitSnapshot> =
    flowOf(UnitSnapshot(timestamp = SystemClock.elapsedRealtimeNanos()))

  override fun onAction(action: WidgetAction) {
    val packageName =
      when (action) {
        is WidgetAction.Tap -> null
        is WidgetAction.Custom -> action.params["packageName"] as? String
        else -> null
      }

    if (packageName == null) return

    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
  }
}
