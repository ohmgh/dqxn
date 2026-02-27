package app.dqxn.android.feature.dashboard.di

import android.content.Context
import androidx.window.layout.WindowInfoTracker
import app.dqxn.android.feature.dashboard.binding.DataProviderRegistryImpl
import app.dqxn.android.feature.dashboard.binding.WidgetRegistryImpl
import app.dqxn.android.feature.dashboard.coordinator.ProviderStatusBridge
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.observability.health.ProviderStatusProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for dashboard singletons: registries and [WindowInfoTracker].
 *
 * Coordinator classes ([LayoutCoordinator], [EditModeCoordinator], [ThemeCoordinator],
 * [WidgetBindingCoordinator], [ProfileCoordinator], [NotificationCoordinator]) are NOT bound here.
 * They use `@Inject constructor` and are injected directly into `DashboardViewModel` which is
 * `@HiltViewModel` (ViewModelScoped).
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class DashboardModule {

  @Binds
  @Singleton
  internal abstract fun bindWidgetRegistry(impl: WidgetRegistryImpl): WidgetRegistry

  @Binds
  @Singleton
  internal abstract fun bindDataProviderRegistry(
    impl: DataProviderRegistryImpl
  ): DataProviderRegistry

  @Binds
  @Singleton
  internal abstract fun bindProviderStatusProvider(
    impl: ProviderStatusBridge
  ): ProviderStatusProvider

  public companion object {

    /**
     * Provides [WindowInfoTracker] for [ConfigurationBoundaryDetector].
     *
     * [WindowInfoTracker.getOrCreate] accepts [Context] (not just Activity). The detector calls
     * `windowInfoTracker.windowLayoutInfo(activity)` at observation time when it receives the
     * Activity reference.
     */
    @Provides
    @Singleton
    public fun provideWindowInfoTracker(
      @ApplicationContext context: Context,
    ): WindowInfoTracker = WindowInfoTracker.getOrCreate(context)
  }
}
