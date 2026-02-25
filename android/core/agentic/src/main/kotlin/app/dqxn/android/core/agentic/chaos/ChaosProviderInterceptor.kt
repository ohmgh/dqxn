package app.dqxn.android.core.agentic.chaos

import app.dqxn.android.sdk.contracts.fault.ProviderFault
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataProviderInterceptor
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
public class ChaosProviderInterceptor @Inject constructor() : DataProviderInterceptor {

  private val activeFaults = ConcurrentHashMap<String, ProviderFault>()

  public fun injectFault(providerId: String, fault: ProviderFault) {
    activeFaults[providerId] = fault
  }

  public fun clearFault(providerId: String) {
    activeFaults.remove(providerId)
  }

  public fun clearAll() {
    activeFaults.clear()
  }

  public fun getActiveFaults(): Map<String, ProviderFault> = activeFaults.toMap()

  override fun <T : DataSnapshot> intercept(
    provider: DataProvider<T>,
    upstream: Flow<T>,
  ): Flow<T> {
    val fault = activeFaults[provider.sourceId] ?: return upstream
    return applyFault(upstream, fault)
  }

  private fun <T : DataSnapshot> applyFault(
    upstream: Flow<T>,
    fault: ProviderFault,
  ): Flow<T> =
    when (fault) {
      is ProviderFault.Kill -> flow { /* emit nothing, flow completes immediately */ }
      is ProviderFault.Stall -> flow { awaitCancellation() }
      is ProviderFault.Error -> flow { throw fault.exception }
      is ProviderFault.Delay ->
        upstream.transformLatest { value ->
          delay(fault.delayMs)
          emit(value)
        }
      is ProviderFault.ErrorOnNext ->
        flow {
          var first = true
          upstream.collect { value ->
            if (first) {
              first = false
              throw fault.exception
            }
            emit(value)
          }
        }
      is ProviderFault.Corrupt ->
        upstream.transformLatest { value ->
          @Suppress("UNCHECKED_CAST")
          emit(fault.transform(value) as T)
        }
      is ProviderFault.Flap ->
        flow {
          coroutineScope {
            val passing = AtomicBoolean(true)
            launch {
              while (true) {
                passing.set(true)
                delay(fault.onMillis)
                passing.set(false)
                delay(fault.offMillis)
              }
            }
            upstream.collect { value ->
              if (passing.get()) {
                emit(value)
              }
            }
          }
        }
    }
}
