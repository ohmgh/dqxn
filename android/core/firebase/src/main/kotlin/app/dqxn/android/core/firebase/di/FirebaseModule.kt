package app.dqxn.android.core.firebase.di

import android.content.Context
import app.dqxn.android.core.firebase.FirebaseAnalyticsTracker
import app.dqxn.android.core.firebase.FirebaseCrashReporter
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.observability.crash.CrashReporter
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public abstract class FirebaseModule {

  @Binds public abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter

  @Binds public abstract fun bindAnalyticsTracker(impl: FirebaseAnalyticsTracker): AnalyticsTracker

  public companion object {

    @Provides
    @Singleton
    public fun provideFirebaseCrashlytics(
      @ApplicationContext context: Context
    ): FirebaseCrashlytics {
      FirebaseApp.initializeApp(context)
      return FirebaseCrashlytics.getInstance()
    }

    @Provides
    @Singleton
    public fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics {
      return FirebaseAnalytics.getInstance(context)
    }

    @Provides
    @Singleton
    public fun provideFirebasePerformance(): FirebasePerformance {
      return FirebasePerformance.getInstance()
    }
  }
}
