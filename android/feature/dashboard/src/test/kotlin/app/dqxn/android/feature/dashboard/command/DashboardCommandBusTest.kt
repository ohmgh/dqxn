package app.dqxn.android.feature.dashboard.command

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class DashboardCommandBusTest {

  @Test
  fun `emitted command is received by collector`() = runTest {
    val bus = DashboardCommandBus()

    bus.commands.test {
      bus.dispatch(DashboardCommand.EnterEditMode)
      assertThat(awaitItem()).isEqualTo(DashboardCommand.EnterEditMode)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `multiple collectors receive the same command`() = runTest {
    val bus = DashboardCommandBus()
    val received1 = mutableListOf<DashboardCommand>()
    val received2 = mutableListOf<DashboardCommand>()

    val job1 = launch { bus.commands.collect { received1.add(it) } }
    val job2 = launch { bus.commands.collect { received2.add(it) } }
    testScheduler.runCurrent()

    bus.dispatch(DashboardCommand.EnterEditMode)
    testScheduler.runCurrent()

    assertThat(received1).containsExactly(DashboardCommand.EnterEditMode)
    assertThat(received2).containsExactly(DashboardCommand.EnterEditMode)

    job1.cancel()
    job2.cancel()
  }

  @Test
  fun `commands are not replayed to late subscribers`() = runTest {
    val bus = DashboardCommandBus()

    // Emit before any collector
    bus.dispatch(DashboardCommand.EnterEditMode)
    testScheduler.runCurrent()

    // Late subscriber should NOT receive the already-emitted command
    bus.commands.test {
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `buffer overflow drops oldest without blocking`() = runTest {
    val bus = DashboardCommandBus()

    // Emit 65 commands (buffer capacity = 64) with no collector
    repeat(65) {
      bus.dispatch(DashboardCommand.EnterEditMode)
    }

    // Now start collector and emit one final command -- should receive it
    bus.commands.test {
      bus.dispatch(DashboardCommand.ExitEditMode)
      assertThat(awaitItem()).isEqualTo(DashboardCommand.ExitEditMode)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
