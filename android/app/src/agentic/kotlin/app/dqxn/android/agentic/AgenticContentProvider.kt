package app.dqxn.android.agentic

import android.database.Cursor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.agentic.android.runtime.AgenticCommandRouter
import dev.agentic.android.runtime.BaseAgenticContentProvider

internal class AgenticContentProvider : BaseAgenticContentProvider() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface AgenticEntryPoint {
    fun commandRouter(): AgenticCommandRouter
  }

  override fun provideRouter(): AgenticCommandRouter? {
    val appContext = context?.applicationContext ?: return null
    return try {
      EntryPointAccessors.fromApplication(appContext, AgenticEntryPoint::class.java).commandRouter()
    } catch (_: IllegalStateException) {
      null
    }
  }

  override fun onQueryPath(pathSegment: String): Cursor? =
    when (pathSegment) {
      "health",
      "anr" -> null
      else -> null
    }
}
