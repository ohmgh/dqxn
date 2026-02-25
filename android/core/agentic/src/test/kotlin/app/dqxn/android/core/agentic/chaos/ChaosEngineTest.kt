package app.dqxn.android.core.agentic.chaos

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class ChaosEngineTest {

  private val providers = listOf("provider-a", "provider-b", "provider-c")

  @Test
  fun `start creates session with correct seed and profile`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    val session = engine.start(42L, "provider-stress", providers, backgroundScope)

    assertThat(session.sessionId).isEqualTo("chaos-42-provider-stress")
    assertThat(session.seed).isEqualTo(42L)
    assertThat(session.profile).isEqualTo("provider-stress")
  }

  @Test
  fun `stop cancels session job and clears interceptor faults`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    val session = engine.start(42L, "provider-stress", providers, backgroundScope)

    // Advance time so some faults are injected
    advanceTimeBy(5000)
    runCurrent()
    assertThat(interceptor.getActiveFaults()).isNotEmpty()

    val summary = engine.stop()

    assertThat(session.job.isCancelled).isTrue()
    assertThat(interceptor.getActiveFaults()).isEmpty()
    assertThat(summary.sessionId).isEqualTo("chaos-42-provider-stress")
    assertThat(summary.seed).isEqualTo(42L)
    assertThat(summary.profile).isEqualTo("provider-stress")
  }

  @Test
  fun `stop on inactive session throws`() {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    val error = assertThrows<IllegalStateException> { engine.stop() }
    assertThat(error).hasMessageThat().contains("No active chaos session")
  }

  @Test
  fun `start while active session throws`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    engine.start(42L, "provider-stress", providers, backgroundScope)

    val error = assertThrows<IllegalStateException> {
      engine.start(99L, "combined", providers, backgroundScope)
    }
    assertThat(error).hasMessageThat().contains("Chaos session already active")

    engine.stop()
  }

  @Test
  fun `isActive returns true during active session`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    assertThat(engine.isActive()).isFalse()

    engine.start(42L, "provider-stress", providers, backgroundScope)
    assertThat(engine.isActive()).isTrue()

    engine.stop()
    assertThat(engine.isActive()).isFalse()
  }

  @Test
  fun `same seed produces same fault sequence`() = runTest {
    val interceptor1 = ChaosProviderInterceptor()
    val engine1 = ChaosEngine(interceptor1)
    engine1.start(42L, "provider-stress", providers, backgroundScope)
    advanceTimeBy(15000)
    runCurrent()
    val summary1 = engine1.stop()

    val interceptor2 = ChaosProviderInterceptor()
    val engine2 = ChaosEngine(interceptor2)
    engine2.start(42L, "provider-stress", providers, backgroundScope)
    advanceTimeBy(15000)
    runCurrent()
    val summary2 = engine2.stop()

    assertThat(summary1.injectedFaults.map { it.faultType })
      .isEqualTo(summary2.injectedFaults.map { it.faultType })
    assertThat(summary1.injectedFaults.map { it.providerId })
      .isEqualTo(summary2.injectedFaults.map { it.providerId })
  }

  @Test
  fun `different seeds produce different fault sequences`() = runTest {
    val interceptor1 = ChaosProviderInterceptor()
    val engine1 = ChaosEngine(interceptor1)
    engine1.start(42L, "provider-stress", providers, backgroundScope)
    advanceTimeBy(15000)
    runCurrent()
    val summary1 = engine1.stop()

    val interceptor2 = ChaosProviderInterceptor()
    val engine2 = ChaosEngine(interceptor2)
    engine2.start(99L, "provider-stress", providers, backgroundScope)
    advanceTimeBy(15000)
    runCurrent()
    val summary2 = engine2.stop()

    // With different seeds, the provider targets or fault types should differ
    val targets1 = summary1.injectedFaults.map { "${it.providerId}:${it.faultType}" }
    val targets2 = summary2.injectedFaults.map { "${it.providerId}:${it.faultType}" }
    assertThat(targets1).isNotEqualTo(targets2)
  }

  @Test
  fun `each of 7 profiles produces non-empty fault plan or is placeholder`() = runTest {
    val nonEmptyProfiles = listOf(
      "provider-stress",
      "provider-flap",
      "widget-storm",
      "process-death",
      "combined",
    )
    val placeholderProfiles = listOf(
      "thermal-ramp",
      "entitlement-churn",
    )

    for (profileName in nonEmptyProfiles) {
      val interceptor = ChaosProviderInterceptor()
      val engine = ChaosEngine(interceptor)
      engine.start(42L, profileName, providers, backgroundScope)
      advanceTimeBy(30000)
      runCurrent()
      val summary = engine.stop()

      assertWithMessage("$profileName should produce non-empty fault plan")
        .that(summary.injectedFaults)
        .isNotEmpty()
    }

    // Placeholder profiles produce empty plans (thermal-ramp, entitlement-churn)
    for (profileName in placeholderProfiles) {
      val interceptor = ChaosProviderInterceptor()
      val engine = ChaosEngine(interceptor)
      engine.start(42L, profileName, providers, backgroundScope)
      advanceTimeBy(30000)
      runCurrent()
      val summary = engine.stop()

      assertWithMessage("$profileName is a placeholder with empty plan")
        .that(summary.injectedFaults)
        .isEmpty()
    }
  }

  @Test
  fun `combined profile includes faults from at least 3 categories`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)
    engine.start(42L, "combined", providers, backgroundScope)
    advanceTimeBy(30000)
    runCurrent()
    val summary = engine.stop()

    // Combined merges ProviderStress, ProviderFlap, WidgetStorm
    // ProviderStress produces Kill/Error, ProviderFlap produces Flap, WidgetStorm produces Corrupt
    val faultCategories = summary.injectedFaults.map { it.faultType }.toSet()
    assertThat(faultCategories.size).isAtLeast(3)
  }

  @Test
  fun `session summary includes all injected faults`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)
    engine.start(42L, "provider-stress", providers, backgroundScope)

    // Advance enough time for all 10 scheduled faults to fire
    advanceTimeBy(30000)
    runCurrent()

    val summary = engine.stop()
    // ProviderStress generates 10 faults
    assertThat(summary.injectedFaults).hasSize(10)
    assertThat(summary.injectedFaults.all { it.providerId.isNotBlank() }).isTrue()
    assertThat(summary.injectedFaults.all { it.faultType.isNotBlank() }).isTrue()
  }

  @Test
  fun `unknown profile name throws IllegalArgumentException`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    val error = assertThrows<IllegalArgumentException> {
      engine.start(42L, "nonexistent-profile", providers, backgroundScope)
    }
    assertThat(error).hasMessageThat().contains("Unknown chaos profile: nonexistent-profile")
  }

  @Test
  fun `currentSession returns active session`() = runTest {
    val interceptor = ChaosProviderInterceptor()
    val engine = ChaosEngine(interceptor)

    assertThat(engine.currentSession()).isNull()

    val session = engine.start(42L, "provider-stress", providers, backgroundScope)
    assertThat(engine.currentSession()).isSameInstanceAs(session)

    engine.stop()
    assertThat(engine.currentSession()).isNull()
  }
}
