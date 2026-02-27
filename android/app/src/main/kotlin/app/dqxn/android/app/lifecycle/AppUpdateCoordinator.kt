package app.dqxn.android.app.lifecycle

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import javax.inject.Inject

/**
 * Coordinates Google Play In-App Update flow (NF-L2).
 * - Priority >= 4: IMMEDIATE update (blocks usage until installed).
 * - Priority < 4: FLEXIBLE update (downloads in background, completes on next restart).
 *
 * Not @Singleton -- created per Activity lifecycle so the update flow is scoped to the visible
 * activity.
 */
public class AppUpdateCoordinator
@Inject
constructor(
  private val appUpdateManager: AppUpdateManager,
) {

  private val installStateListener = InstallStateUpdatedListener { state ->
    if (state.installStatus() == InstallStatus.DOWNLOADED) {
      appUpdateManager.completeUpdate()
    }
  }

  /**
   * Checks for available updates and starts the appropriate flow.
   *
   * @param activity The activity to display the update UI on.
   */
  public fun checkForUpdate(activity: Activity) {
    appUpdateManager.registerListener(installStateListener)
    appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
      if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
        val updateType =
          if (appUpdateInfo.updatePriority() >= CRITICAL_PRIORITY_THRESHOLD) {
            AppUpdateType.IMMEDIATE
          } else {
            AppUpdateType.FLEXIBLE
          }

        if (appUpdateInfo.isUpdateTypeAllowed(updateType)) {
          appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            activity,
            AppUpdateOptions.newBuilder(updateType).build(),
            UPDATE_REQUEST_CODE,
          )
        }
      }
    }
  }

  /** Unregisters the install state listener. Call from Activity.onDestroy(). */
  public fun unregisterListener() {
    appUpdateManager.unregisterListener(installStateListener)
  }

  private companion object {
    /** Updates with priority >= 4 are considered critical and trigger IMMEDIATE flow. */
    const val CRITICAL_PRIORITY_THRESHOLD = 4

    /** Request code for the update flow activity result. */
    const val UPDATE_REQUEST_CODE = 1001
  }
}
