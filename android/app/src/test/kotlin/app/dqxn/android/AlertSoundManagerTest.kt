package app.dqxn.android

import app.dqxn.android.sdk.contracts.notification.AlertEmitter
import app.dqxn.android.sdk.contracts.notification.AlertMode
import app.dqxn.android.sdk.contracts.notification.AlertProfile
import app.dqxn.android.sdk.contracts.notification.AlertResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class AlertSoundManagerTest {

  private val manager = AlertSoundManager()

  @Test
  fun `fire returns UNAVAILABLE for all profiles`() = runTest {
    val profile = AlertProfile(mode = AlertMode.SOUND)
    assertThat(manager.fire(profile)).isEqualTo(AlertResult.UNAVAILABLE)
  }

  @Test
  fun `fire returns UNAVAILABLE for vibrate mode`() = runTest {
    val profile = AlertProfile(mode = AlertMode.VIBRATE)
    assertThat(manager.fire(profile)).isEqualTo(AlertResult.UNAVAILABLE)
  }

  @Test
  fun `fire returns UNAVAILABLE for silent mode`() = runTest {
    val profile = AlertProfile(mode = AlertMode.SILENT)
    assertThat(manager.fire(profile)).isEqualTo(AlertResult.UNAVAILABLE)
  }

  @Test
  fun `implements AlertEmitter interface`() {
    // Compile-time verification that AlertSoundManager is an AlertEmitter
    val emitter: AlertEmitter = manager
    assertThat(emitter).isNotNull()
  }
}
