package app.dqxn.android.feature.settings.di

import app.dqxn.android.feature.settings.setup.SetupEvaluatorImpl
import app.dqxn.android.sdk.contracts.setup.SetupEvaluator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
  @Binds abstract fun bindSetupEvaluator(impl: SetupEvaluatorImpl): SetupEvaluator
}
