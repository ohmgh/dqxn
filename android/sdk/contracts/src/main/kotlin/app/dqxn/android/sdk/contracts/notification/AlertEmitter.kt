package app.dqxn.android.sdk.contracts.notification

/**
 * Fires an alert (sound, vibration, TTS) for a notification.
 *
 * Interface only in `:sdk:contracts`. Implementation (`AlertSoundManager`, `@Singleton`) deferred
 * to Phase 7.
 */
public interface AlertEmitter {
  public suspend fun fire(profile: AlertProfile): AlertResult
}
