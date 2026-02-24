package app.dqxn.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
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
      // Blank canvas placeholder -- real DashboardShell lands in Phase 7
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF0F172A))
          .testTag("dashboard_grid"),
      )
    }
  }
}
