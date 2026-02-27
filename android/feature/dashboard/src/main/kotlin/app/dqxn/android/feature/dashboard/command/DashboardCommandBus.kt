package app.dqxn.android.feature.dashboard.command

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton-scoped command relay between [AgenticContentProvider] handlers (SingletonComponent) and
 * [DashboardViewModel] (ViewModelRetainedComponent).
 *
 * Uses [MutableSharedFlow] with [BufferOverflow.DROP_OLDEST] to ensure emission never suspends,
 * even when no collector is active. Commands are NOT replayed to late subscribers -- they are
 * one-shot discrete events.
 */
@Singleton
public class DashboardCommandBus @Inject constructor() {

  private val _commands =
    MutableSharedFlow<DashboardCommand>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  public val commands: SharedFlow<DashboardCommand> = _commands.asSharedFlow()

  public suspend fun dispatch(command: DashboardCommand) {
    _commands.emit(command)
  }
}
