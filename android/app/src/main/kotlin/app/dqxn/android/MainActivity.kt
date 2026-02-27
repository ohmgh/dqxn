package app.dqxn.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import app.dqxn.android.app.lifecycle.AppReviewCoordinator
import app.dqxn.android.app.lifecycle.AppUpdateCoordinator
import app.dqxn.android.feature.dashboard.DashboardScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
public class MainActivity : ComponentActivity() {

  @Inject internal lateinit var appUpdateCoordinator: AppUpdateCoordinator

  @Inject internal lateinit var appReviewCoordinator: AppReviewCoordinator

  @Inject internal lateinit var crashRecovery: CrashRecovery

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WindowInsetsControllerCompat(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }

    setContent {
      DashboardScreen()

      LaunchedEffect(Unit) {
        appReviewCoordinator.maybeRequestReview(
          activity = this@MainActivity,
          hasCustomizedLayout = true, // TODO: derive from LayoutRepository widget count
          hasCrashedThisSession = crashRecovery.isInSafeMode(),
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    appUpdateCoordinator.checkForUpdate(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    appUpdateCoordinator.unregisterListener()
  }
}
