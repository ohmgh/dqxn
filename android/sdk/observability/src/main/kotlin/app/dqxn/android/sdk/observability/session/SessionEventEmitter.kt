package app.dqxn.android.sdk.observability.session

/**
 * Interface for recording session events. Lives in `:sdk:observability` so any feature module can
 * emit events without depending on `:feature:diagnostics`.
 *
 * Implementation: SessionRecorder in `:feature:diagnostics`, bound via Hilt.
 */
public interface SessionEventEmitter {
  /** Record a session event. No-op if recording is not enabled. */
  public fun record(event: SessionEvent)
}
