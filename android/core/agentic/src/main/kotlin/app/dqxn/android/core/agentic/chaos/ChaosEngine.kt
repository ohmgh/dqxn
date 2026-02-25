package app.dqxn.android.core.agentic.chaos

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Orchestrates deterministic fault injection sessions using seed-based [kotlin.random.Random].
 *
 * Given the same seed and profile, [start] produces an identical fault sequence across runs (SC4).
 * Only one session may be active at a time. [stop] cancels the session job, clears all injected
 * faults, and returns a summary of what was injected.
 *
 * The engine delegates fault application to [ChaosProviderInterceptor] and uses [ChaosProfile]
 * for plan generation.
 */
@Singleton
public class ChaosEngine @Inject constructor(
  private val interceptor: ChaosProviderInterceptor,
) {

  private var activeSession: ChaosSession? = null

  /**
   * Starts a new chaos session with deterministic fault scheduling.
   *
   * @param seed Random seed for deterministic reproduction.
   * @param profileName Name of the [ChaosProfile] to use (e.g., "provider-stress", "combined").
   * @param providerIds List of provider source IDs to target.
   * @param scope Parent coroutine scope; the session job is a child of this scope's job.
   * @return The created [ChaosSession].
   * @throws IllegalStateException if a session is already active.
   * @throws IllegalArgumentException if [profileName] does not match any known profile.
   */
  public fun start(
    seed: Long,
    profileName: String,
    providerIds: List<String>,
    scope: CoroutineScope,
  ): ChaosSession {
    check(activeSession == null) { "Chaos session already active" }

    val random = kotlin.random.Random(seed)
    val profile =
      ChaosProfile.fromName(profileName)
        ?: throw IllegalArgumentException("Unknown chaos profile: $profileName")
    val plan = profile.generatePlan(random, providerIds)
    val sessionJob = Job(scope.coroutineContext[Job])
    val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)

    val session = ChaosSession(
      sessionId = "chaos-$seed-$profileName",
      seed = seed,
      profile = profileName,
      job = sessionJob,
    )
    activeSession = session

    sessionScope.launch {
      var previousDelayMs = 0L
      for (scheduledFault in plan) {
        // delayMs is absolute offset from session start; compute delta from previous fault
        val delta = scheduledFault.delayMs - previousDelayMs
        if (delta > 0) delay(delta)
        previousDelayMs = scheduledFault.delayMs
        interceptor.injectFault(scheduledFault.providerId, scheduledFault.fault)
        session.recordInjection(scheduledFault, atMs = System.currentTimeMillis())
      }
    }
    return session
  }

  /**
   * Stops the active chaos session, cancels its job, clears all injected faults, and returns
   * a summary of the session.
   *
   * @return The [ChaosSessionSummary] for the stopped session.
   * @throws IllegalStateException if no session is active.
   */
  public fun stop(): ChaosSessionSummary {
    val session = activeSession ?: error("No active chaos session")
    session.job.cancel()
    interceptor.clearAll()
    activeSession = null
    return session.toSummary()
  }

  /** Returns true if a chaos session is currently active. */
  public fun isActive(): Boolean = activeSession != null

  /** Returns the current active session, or null if none. */
  public fun currentSession(): ChaosSession? = activeSession
}
