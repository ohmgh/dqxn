package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.fault.ProviderFault
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest

/**
 * Configurable fake [DataProvider] with mid-stream [ProviderFault] injection.
 *
 * The [baseFlow] provides the underlying data stream. Call [injectFault] to transform emissions
 * in-flight: delays, errors, kills, stalls, corruption, and connection flapping.
 */
public class TestDataProvider<T : DataSnapshot>(
  override val sourceId: String = "test:provider",
  override val displayName: String = "Test Provider",
  override val description: String = "A test provider",
  override val dataType: String = DataTypes.SPEED,
  override val priority: ProviderPriority = ProviderPriority.SIMULATED,
  override val snapshotType: KClass<T>,
  override val schema: DataSchema = DataSchema(emptyList(), 3000L),
  override val setupSchema: List<SetupPageDefinition> = emptyList(),
  override val subscriberTimeout: Duration = 5.seconds,
  override val firstEmissionTimeout: Duration = 5.seconds,
  override val requiredAnyEntitlement: Set<String>? = null,
  private val baseFlow: Flow<T>,
) : DataProvider<T> {

  private val _fault: MutableStateFlow<ProviderFault?> = MutableStateFlow(null)

  override val isAvailable: Boolean = true
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)

  /** Inject a [ProviderFault] to transform the emission stream. Pass `null` to clear. */
  public fun injectFault(fault: ProviderFault?) {
    _fault.value = fault
  }

  override fun provideState(): Flow<T> =
    baseFlow.transformLatest { snapshot ->
      when (val fault = _fault.value) {
        null -> emit(snapshot)
        is ProviderFault.Kill -> {
          return@transformLatest // stop emitting
        }
        is ProviderFault.Delay -> {
          delay(fault.delayMs)
          emit(snapshot)
        }
        is ProviderFault.Error -> throw fault.exception
        is ProviderFault.ErrorOnNext -> {
          _fault.value = null
          throw fault.exception
        }
        is ProviderFault.Corrupt -> {
          @Suppress("UNCHECKED_CAST") emit(fault.transform(snapshot) as T)
        }
        is ProviderFault.Flap -> {
          emit(snapshot)
          delay(fault.onMillis)
          // pause for offMillis handled by caller
        }
        is ProviderFault.Stall -> {
          awaitCancellation() // never emit
        }
      }
    }
}
