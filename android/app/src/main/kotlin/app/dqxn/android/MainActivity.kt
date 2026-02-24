package app.dqxn.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import app.dqxn.android.feature.dashboard.DashboardScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
public class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WindowInsetsControllerCompat(window, window.decorView).apply {
      systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }

    setContent {
      DashboardScreen()
    }
  }
}
