package app.dqxn.android.sdk.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
public object DispatcherModule {

  @Provides @IoDispatcher public fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @Provides
  @DefaultDispatcher
  public fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

  @Provides
  @MainDispatcher
  public fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

  @Provides
  @Singleton
  @ApplicationScope
  public fun providesApplicationScope(
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
  ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}
