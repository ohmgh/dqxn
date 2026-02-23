package app.dqxn.android.sdk.contracts.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DashboardWidget(
  val typeId: String,
  val displayName: String,
  val icon: String = "",
)
