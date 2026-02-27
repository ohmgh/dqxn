package app.dqxn.android.agentic.chaos

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataProviderInterceptor
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import dev.agentic.android.chaos.Fault
import dev.agentic.android.chaos.FaultInjector
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
public class ChaosProviderInterceptor @Inject constructor() : DataProviderInterceptor, FaultInjector {

  private val activeFaultsMap = ConcurrentHashMap<String, Fault>()

  override fun inject(targetId: String, fault: Fault) {
    activeFaultsMap[targetId] = fault
  }

  override fun clear(targetId: String) {
    activeFaultsMap.remove(targetId)
  }

  override fun clearAll() {
    activeFaultsMap.clear()
  }

  override fun activeFaults(): Map<String, Fault> = activeFaultsMap.toMap()

  override fun <T : DataSnapshot> intercept(
    provider: DataProvider<T>,
    upstream: Flow<T>,
  ): Flow<T> {
    val fault = activeFaultsMap[provider.sourceId] ?: return upstream
    return applyFault(upstream, fault)
  }

  private fun <T : DataSnapshot> applyFault(
    upstream: Flow<T>,
    fault: Fault,
  ): Flow<T> =
    when (fault) {
      is Fault.Kill -> flow { /* emit nothing, flow completes immediately */ }
      is Fault.Stall -> flow { awaitCancellation() }
      is Fault.Error -> flow { throw fault.exception }
      is Fault.Delay ->
        upstream.transformLatest { value ->
          delay(fault.delayMs)
          emit(value)
        }
      is Fault.ErrorOnNext ->
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
      is Fault.Corrupt ->
        upstream.transformLatest { value ->
          @Suppress("UNCHECKED_CAST")
          emit(fault.transform(value) as T)
        }
      is Fault.Flap ->
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
