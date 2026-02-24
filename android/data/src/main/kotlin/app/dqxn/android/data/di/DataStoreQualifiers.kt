package app.dqxn.android.data.di

import javax.inject.Qualifier

/** Preferences DataStore for user settings (theme mode, orientation, etc.). */
@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class UserPreferences

/** Preferences DataStore for per-provider settings ({packId}:{providerId}:{key}). */
@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class ProviderSettings

/** Preferences DataStore for per-widget style overrides. */
@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class WidgetStyles
