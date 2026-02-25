package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.AmbientLightSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic ambient light provider for demo mode. Emits [AmbientLightSnapshot] every 2 seconds
 * with a sine wave simulating a day/night cycle: full cycle in 60 ticks (120 seconds). Zero
 * randomness -- same tick always produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-ambient-light",
  displayName = "Demo Ambient Light",
  description = "Deterministic simulated ambient light for demo mode",
)
@Singleton
public class DemoAmbientLightProvider @Inject constructor() : DataProvider<AmbientLightSnapshot> {

  override val snapshotType: KClass<AmbientLightSnapshot> = AmbientLightSnapshot::class
  override val sourceId: String = "demo:ambient-light"
  override val displayName: String = "Demo Ambient Light"
  override val description: String = "Deterministic simulated ambient light for demo mode"
  override val dataType: String = DataTypes.AMBIENT_LIGHT
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
          DataFieldSpec(name = "lux", typeId = "Float", unit = "lx"),
          DataFieldSpec(name = "category", typeId = "String"),
        ),
      stalenessThresholdMs = 4000L,
    )

  override fun provideState(): Flow<AmbientLightSnapshot> = flow {
    var tick = 0
    while (true) {
      val lux = LUX_MIDPOINT + LUX_AMPLITUDE * sin(tick * PI / HALF_CYCLE).toFloat()
      val category = categorize(lux)
      emit(
        AmbientLightSnapshot(
          lux = lux,
          category = category,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    const val LUX_MIDPOINT: Float = 500.0f
    const val LUX_AMPLITUDE: Float = 499.0f
    const val HALF_CYCLE: Int = 30
    const val INTERVAL_MS: Long = 2000L

    fun categorize(lux: Float): String =
      when {
        lux < 10f -> "DARK"
        lux < 100f -> "DIM"
        lux < 500f -> "NORMAL"
        else -> "BRIGHT"
      }
  }
}
