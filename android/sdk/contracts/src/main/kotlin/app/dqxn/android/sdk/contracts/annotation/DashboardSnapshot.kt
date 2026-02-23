package app.dqxn.android.sdk.contracts.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DashboardSnapshot(
  val dataType: String,
)
