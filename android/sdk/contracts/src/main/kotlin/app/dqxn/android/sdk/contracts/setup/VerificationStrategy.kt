package app.dqxn.android.sdk.contracts.setup

/**
 * Strategy for verifying that an instruction step was completed successfully.
 *
 * Interface only in `:sdk:contracts` -- implementations (`SystemServiceVerification`,
 * `ClipboardVerification`, etc.) have `Context` dependency and live in `:feature:settings`
 * (Phase 10) or `:sdk:ui` (Phase 3).
 */
public interface VerificationStrategy {
  public suspend fun verify(): VerificationResult
}

public sealed interface VerificationResult {
  public data object Verified : VerificationResult

  public data class Failed(val message: String) : VerificationResult

  public data object Skipped : VerificationResult
}
