package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.OrientationSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic orientation provider for demo mode. Emits [OrientationSnapshot] every 500ms with
 * slow heading rotation (5 degrees per tick, full circle in 36 seconds). Zero randomness -- same
 * tick always produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-orientation",
  displayName = "Demo Orientation",
  description = "Deterministic simulated compass for demo mode",
)
@Singleton
public class DemoOrientationProvider @Inject constructor() : DataProvider<OrientationSnapshot> {

  override val snapshotType: KClass<OrientationSnapshot> = OrientationSnapshot::class
  override val sourceId: String = "demo:orientation"
  override val displayName: String = "Demo Orientation"
  override val description: String = "Deterministic simulated compass for demo mode"
  override val dataType: String = DataTypes.ORIENTATION
  override val priority: ProviderPriority = ProviderPriority.SIMULATED
  override val subscriberTimeout: Duration = 2.seconds
  override val firstEmissionTimeout: Duration = 3.seconds
  override val isAvailable: Boolean = true
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(name = "bearing", typeId = "Float", unit = "degrees"),
          DataFieldSpec(name = "pitch", typeId = "Float", unit = "degrees"),
          DataFieldSpec(name = "roll", typeId = "Float", unit = "degrees"),
        ),
      stalenessThresholdMs = 1000L,
    )

  override fun provideState(): Flow<OrientationSnapshot> = flow {
    var tick = 0
    while (true) {
      emit(
        OrientationSnapshot(
          bearing = (tick * DEGREES_PER_TICK) % FULL_ROTATION,
          pitch = 0f,
          roll = 0f,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    const val DEGREES_PER_TICK: Float = 5.0f
    const val FULL_ROTATION: Float = 360.0f
    const val INTERVAL_MS: Long = 500L
  }
}
