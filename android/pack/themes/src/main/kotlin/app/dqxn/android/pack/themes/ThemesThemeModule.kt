package app.dqxn.android.pack.themes

import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Manual Hilt module for theme provider binding. KSP only generates widget/provider bindings. */
@Module
@InstallIn(SingletonComponent::class)
public interface ThemesThemeModule {

  @Binds
  @IntoSet
  public fun bindThemesThemeProvider(impl: ThemesPackThemeProvider): ThemeProvider
}
