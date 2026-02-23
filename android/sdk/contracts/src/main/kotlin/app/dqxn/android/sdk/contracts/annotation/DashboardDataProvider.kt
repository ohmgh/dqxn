package app.dqxn.android.sdk.contracts.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DashboardDataProvider(
  val localId: String,
  val displayName: String,
  val description: String = "",
)
