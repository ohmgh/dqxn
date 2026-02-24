package app.dqxn.android.di

import android.content.Context
import app.dqxn.android.AlertSoundManager
import app.dqxn.android.CrashRecovery
import app.dqxn.android.StubEntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.notification.AlertEmitter
import app.dqxn.android.sdk.contracts.pack.DashboardPackManifest
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataProviderInterceptor
import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public abstract class AppModule {

  @Multibinds
  internal abstract fun widgetRenderers(): Set<WidgetRenderer>

  @Multibinds
  internal abstract fun dataProviders(): Set<@JvmSuppressWildcards DataProvider<*>>

  @Multibinds
  internal abstract fun themeProviders(): Set<ThemeProvider>

  @Multibinds
  internal abstract fun dataProviderInterceptors(): Set<DataProviderInterceptor>

  @Multibinds
  internal abstract fun packManifests(): Set<DashboardPackManifest>

  public companion object {

    @Provides
    @Singleton
    public fun provideAlertEmitter(): AlertEmitter = AlertSoundManager()

    @Provides
    @Singleton
    public fun provideCrashRecovery(
      @ApplicationContext context: Context,
    ): CrashRecovery = CrashRecovery(
      context.getSharedPreferences("crash_recovery", Context.MODE_PRIVATE),
    )

    @Provides
    @Singleton
    public fun provideEntitlementManager(): EntitlementManager =
      StubEntitlementManager()
  }
}
