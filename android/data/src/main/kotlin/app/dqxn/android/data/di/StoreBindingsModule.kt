package app.dqxn.android.data.di

import app.dqxn.android.data.device.ConnectionEventStore
import app.dqxn.android.data.device.ConnectionEventStoreImpl
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.data.device.PairedDeviceStoreImpl
import app.dqxn.android.data.provider.ProviderSettingsStore
import app.dqxn.android.data.provider.ProviderSettingsStoreImpl
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.data.style.WidgetStyleStoreImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
public abstract class StoreBindingsModule {

  @Binds
  public abstract fun bindProviderSettingsStore(
    impl: ProviderSettingsStoreImpl,
  ): ProviderSettingsStore

  @Binds public abstract fun bindPairedDeviceStore(impl: PairedDeviceStoreImpl): PairedDeviceStore

  @Binds
  public abstract fun bindConnectionEventStore(impl: ConnectionEventStoreImpl): ConnectionEventStore

  @Binds public abstract fun bindWidgetStyleStore(impl: WidgetStyleStoreImpl): WidgetStyleStore
}
