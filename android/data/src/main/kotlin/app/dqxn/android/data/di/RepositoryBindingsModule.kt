package app.dqxn.android.data.di

import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.layout.LayoutRepositoryImpl
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.data.preferences.UserPreferencesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
public abstract class RepositoryBindingsModule {

  @Binds public abstract fun bindLayoutRepository(impl: LayoutRepositoryImpl): LayoutRepository

  @Binds
  public abstract fun bindUserPreferencesRepository(
    impl: UserPreferencesRepositoryImpl,
  ): UserPreferencesRepository
}
