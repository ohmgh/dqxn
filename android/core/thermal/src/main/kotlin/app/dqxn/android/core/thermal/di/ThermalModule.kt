package app.dqxn.android.core.thermal.di

import app.dqxn.android.core.thermal.ThermalManager
import app.dqxn.android.core.thermal.ThermalMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ThermalModule {

  @Binds abstract fun bindThermalMonitor(impl: ThermalManager): ThermalMonitor
}
