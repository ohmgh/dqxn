package app.dqxn.android.feature.diagnostics.di

import app.dqxn.android.feature.diagnostics.SessionRecorder
import app.dqxn.android.sdk.observability.session.SessionEventEmitter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module binding [SessionRecorder] to [SessionEventEmitter].
 *
 * This makes [SessionEventEmitter] injectable anywhere in the app graph, including
 * `:feature:dashboard` which emits events without depending on `:feature:diagnostics`.
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class DiagnosticsModule {

  @Binds public abstract fun bindSessionEventEmitter(impl: SessionRecorder): SessionEventEmitter
}
