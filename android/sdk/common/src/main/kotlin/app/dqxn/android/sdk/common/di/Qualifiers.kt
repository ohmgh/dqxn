package app.dqxn.android.sdk.common.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class DefaultDispatcher

@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class MainDispatcher

@Qualifier @Retention(AnnotationRetention.RUNTIME) public annotation class ApplicationScope
