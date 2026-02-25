package app.dqxn.android.core.agentic.chaos

import kotlinx.coroutines.Job

public data class ChaosSession(
  val sessionId: String,
  val seed: Long,
  val profile: String,
  val job: Job,
  val startedAtMs: Long = System.currentTimeMillis(),
) {

  private val injections = mutableListOf<InjectedFault>()

  public fun recordInjection(fault: ScheduledFault, atMs: Long) {
    injections.add(
      InjectedFault(
        providerId = fault.providerId,
        faultType = fault.fault.toString(),
        description = fault.description,
        atMs = atMs,
      )
    )
  }

  public fun toSummary(): ChaosSessionSummary =
    ChaosSessionSummary(
      sessionId = sessionId,
      seed = seed,
      profile = profile,
      injectedFaults = injections.toList(),
      durationMs = System.currentTimeMillis() - startedAtMs,
    )
}

public data class InjectedFault(
  val providerId: String,
  val faultType: String,
  val description: String,
  val atMs: Long,
)

public data class ChaosSessionSummary(
  val sessionId: String,
  val seed: Long,
  val profile: String,
  val injectedFaults: List<InjectedFault>,
  val durationMs: Long,
)
