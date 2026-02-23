package app.dqxn.android.sdk.common.result

public sealed interface AppError {
  public val message: String?

  public data class Network(
    override val message: String? = null,
    val cause: Throwable? = null,
  ) : AppError

  public data class Bluetooth(
    override val message: String? = null,
  ) : AppError

  public data class Permission(
    val kind: PermissionKind,
    override val message: String? = null,
  ) : AppError

  public data class Device(
    override val message: String? = null,
  ) : AppError

  public data class Database(
    override val message: String? = null,
    val cause: Throwable? = null,
  ) : AppError

  public data class Pack(
    val packId: String,
    val code: String,
    override val message: String? = null,
  ) : AppError

  public data class Unknown(
    override val message: String? = null,
    val cause: Throwable? = null,
  ) : AppError
}
