package app.dqxn.android.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.dqxn.android.data.proto.CustomThemeStoreProto
import app.dqxn.android.data.proto.DashboardStoreProto
import app.dqxn.android.data.proto.PairedDeviceStoreProto
import app.dqxn.android.data.serializer.CustomThemeSerializer
import app.dqxn.android.data.serializer.DashboardStoreSerializer
import app.dqxn.android.data.serializer.PairedDeviceSerializer
import app.dqxn.android.sdk.observability.crash.ErrorContext
import app.dqxn.android.sdk.observability.crash.ErrorReporter
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.error
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

private val TAG = LogTag("DataModule")

@Module
@InstallIn(SingletonComponent::class)
public object DataModule {

  // ---------------------------------------------------------------------------
  // Proto DataStores (3)
  // ---------------------------------------------------------------------------

  @Provides
  @Singleton
  public fun provideDashboardDataStore(
    @ApplicationContext context: Context,
    logger: DqxnLogger,
    errorReporter: ErrorReporter,
  ): DataStore<DashboardStoreProto> =
    DataStoreFactory.create(
      serializer = DashboardStoreSerializer,
      corruptionHandler =
        ReplaceFileCorruptionHandler { e ->
          logger.error(TAG) { "DashboardStore corrupted, resetting to defaults" }
          errorReporter.reportNonFatal(e, ErrorContext.System("DashboardStore"))
          DashboardStoreProto.getDefaultInstance()
        },
      produceFile = { File(context.filesDir, "datastore/dashboard_store.pb") },
    )

  @Provides
  @Singleton
  public fun providePairedDeviceDataStore(
    @ApplicationContext context: Context,
    logger: DqxnLogger,
    errorReporter: ErrorReporter,
  ): DataStore<PairedDeviceStoreProto> =
    DataStoreFactory.create(
      serializer = PairedDeviceSerializer,
      corruptionHandler =
        ReplaceFileCorruptionHandler { e ->
          logger.error(TAG) { "PairedDeviceStore corrupted, resetting to defaults" }
          errorReporter.reportNonFatal(e, ErrorContext.System("PairedDeviceStore"))
          PairedDeviceStoreProto.getDefaultInstance()
        },
      produceFile = { File(context.filesDir, "datastore/paired_devices.pb") },
    )

  @Provides
  @Singleton
  public fun provideCustomThemeDataStore(
    @ApplicationContext context: Context,
    logger: DqxnLogger,
    errorReporter: ErrorReporter,
  ): DataStore<CustomThemeStoreProto> =
    DataStoreFactory.create(
      serializer = CustomThemeSerializer,
      corruptionHandler =
        ReplaceFileCorruptionHandler { e ->
          logger.error(TAG) { "CustomThemeStore corrupted, resetting to defaults" }
          errorReporter.reportNonFatal(e, ErrorContext.System("CustomThemeStore"))
          CustomThemeStoreProto.getDefaultInstance()
        },
      produceFile = { File(context.filesDir, "datastore/custom_themes.pb") },
    )

  // ---------------------------------------------------------------------------
  // Preferences DataStores (3)
  // ---------------------------------------------------------------------------

  @Provides
  @Singleton
  @UserPreferences
  public fun provideUserPreferencesDataStore(
    @ApplicationContext context: Context,
  ): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
      produceFile = { File(context.filesDir, "datastore/user_preferences.preferences_pb") },
    )

  @Provides
  @Singleton
  @ProviderSettings
  public fun provideProviderSettingsDataStore(
    @ApplicationContext context: Context,
  ): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
      produceFile = { File(context.filesDir, "datastore/provider_settings.preferences_pb") },
    )

  @Provides
  @Singleton
  @WidgetStyles
  public fun provideWidgetStyleDataStore(
    @ApplicationContext context: Context,
  ): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
      produceFile = { File(context.filesDir, "datastore/widget_styles.preferences_pb") },
    )
}
