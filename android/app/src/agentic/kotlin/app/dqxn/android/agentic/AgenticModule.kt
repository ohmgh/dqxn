package app.dqxn.android.agentic

import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.chaos.ChaosProviderInterceptor
import app.dqxn.android.sdk.contracts.provider.DataProviderInterceptor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * Hilt module providing agentic framework bindings shared by debug and benchmark builds.
 *
 * - [commandHandlers]: empty multibinding set populated by KSP-generated `AgenticHiltModule`
 * - [bindChaosInterceptor]: registers [ChaosProviderInterceptor] into the data provider
 *   interceptor chain for chaos testing
 *
 * Lives in `src/agentic/` which is included in both debug and benchmark source sets.
 * Not compiled into release builds.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AgenticModule {

  @Multibinds
  abstract fun commandHandlers(): Set<CommandHandler>

  @Binds
  @IntoSet
  abstract fun bindChaosInterceptor(
    interceptor: ChaosProviderInterceptor,
  ): DataProviderInterceptor
}
