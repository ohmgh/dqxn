package app.dqxn.android

import app.dqxn.android.sdk.contracts.notification.AlertEmitter
import app.dqxn.android.sdk.contracts.notification.AlertProfile
import app.dqxn.android.sdk.contracts.notification.AlertResult

/**
 * Stub [AlertEmitter] implementation for Phase 6.
 *
 * Returns [AlertResult.UNAVAILABLE] for all [fire] calls because no audio infrastructure
 * (SoundPool, AudioManager, Vibrator) is wired yet. Real implementation with SoundPool lands
 * incrementally in Phase 7 when NotificationCoordinator is built.
 */
public class AlertSoundManager : AlertEmitter {

  override suspend fun fire(profile: AlertProfile): AlertResult = AlertResult.UNAVAILABLE
}
