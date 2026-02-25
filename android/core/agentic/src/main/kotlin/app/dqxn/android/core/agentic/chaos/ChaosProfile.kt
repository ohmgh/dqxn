package app.dqxn.android.core.agentic.chaos

import app.dqxn.android.sdk.contracts.fault.ProviderFault
import kotlin.random.Random

public data class ScheduledFault(
  val delayMs: Long,
  val providerId: String,
  val fault: ProviderFault,
  val description: String,
)

public sealed interface ChaosProfile {

  public val name: String

  public fun generatePlan(random: Random, providerIds: List<String>): List<ScheduledFault>

  public data object ProviderStress : ChaosProfile {
    override val name: String = "provider-stress"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      if (providerIds.isEmpty()) return emptyList()
      val faults = mutableListOf<ScheduledFault>()
      var currentDelayMs = 0L
      repeat(10) {
        val interval = random.nextLong(500, 2001)
        currentDelayMs += interval
        val providerId = providerIds[random.nextInt(providerIds.size)]
        val fault =
          if (random.nextBoolean()) {
            ProviderFault.Kill
          } else {
            ProviderFault.Error(RuntimeException("Chaos stress fault"))
          }
        faults.add(
          ScheduledFault(
            delayMs = currentDelayMs,
            providerId = providerId,
            fault = fault,
            description = "Stress: ${fault::class.simpleName} on $providerId",
          )
        )
      }
      return faults.sortedBy { it.delayMs }
    }
  }

  public data object ProviderFlap : ChaosProfile {
    override val name: String = "provider-flap"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      if (providerIds.isEmpty()) return emptyList()
      val faults = mutableListOf<ScheduledFault>()
      for (providerId in providerIds.take(3)) {
        val onMs = random.nextLong(1000, 3001)
        val offMs = random.nextLong(1000, 3001)
        faults.add(
          ScheduledFault(
            delayMs = random.nextLong(0, 2000),
            providerId = providerId,
            fault = ProviderFault.Flap(onMillis = onMs, offMillis = offMs),
            description = "Flap: ${onMs}ms on / ${offMs}ms off on $providerId",
          )
        )
      }
      return faults.sortedBy { it.delayMs }
    }
  }

  public data object ThermalRamp : ChaosProfile {
    override val name: String = "thermal-ramp"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      // Placeholder: thermal injection is a separate system.
      // Records intent only; actual thermal simulation handled by ThermalManager.
      return emptyList()
    }
  }

  public data object EntitlementChurn : ChaosProfile {
    override val name: String = "entitlement-churn"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      // Placeholder: entitlement simulation handled by StubEntitlementManager.
      // ChaosEngine in Plan 05 bridges this profile to simulateRevocation/simulateGrant.
      return emptyList()
    }
  }

  public data object WidgetStorm : ChaosProfile {
    override val name: String = "widget-storm"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      if (providerIds.isEmpty()) return emptyList()
      val faults = mutableListOf<ScheduledFault>()
      var currentDelayMs = 0L
      repeat(8) {
        val interval = random.nextLong(200, 1001)
        currentDelayMs += interval
        val providerId = providerIds[random.nextInt(providerIds.size)]
        faults.add(
          ScheduledFault(
            delayMs = currentDelayMs,
            providerId = providerId,
            fault =
              ProviderFault.Corrupt { snapshot ->
                // Return the snapshot unchanged -- real corruption would need
                // snapshot-type-specific transforms applied by ChaosEngine
                snapshot
              },
            description = "Storm: Corrupt extreme values on $providerId",
          )
        )
      }
      return faults.sortedBy { it.delayMs }
    }
  }

  public data object ProcessDeath : ChaosProfile {
    override val name: String = "process-death"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      if (providerIds.isEmpty()) return emptyList()
      val killDelay = random.nextLong(1000, 5001)
      return providerIds.map { providerId ->
        ScheduledFault(
          delayMs = killDelay,
          providerId = providerId,
          fault = ProviderFault.Kill,
          description = "ProcessDeath: Kill all providers at ${killDelay}ms",
        )
      }
    }
  }

  public data object Combined : ChaosProfile {
    override val name: String = "combined"

    override fun generatePlan(
      random: Random,
      providerIds: List<String>,
    ): List<ScheduledFault> {
      val profiles =
        listOf(
          ProviderStress,
          ProviderFlap,
          WidgetStorm,
        )
      return profiles
        .flatMap { it.generatePlan(random, providerIds) }
        .sortedBy { it.delayMs }
    }
  }

  public companion object {
    public fun fromName(name: String): ChaosProfile? =
      when (name) {
        ProviderStress.name -> ProviderStress
        ProviderFlap.name -> ProviderFlap
        ThermalRamp.name -> ThermalRamp
        EntitlementChurn.name -> EntitlementChurn
        WidgetStorm.name -> WidgetStorm
        ProcessDeath.name -> ProcessDeath
        Combined.name -> Combined
        else -> null
      }

    public fun all(): List<ChaosProfile> =
      listOf(
        ProviderStress,
        ProviderFlap,
        ThermalRamp,
        EntitlementChurn,
        WidgetStorm,
        ProcessDeath,
        Combined,
      )
  }
}
