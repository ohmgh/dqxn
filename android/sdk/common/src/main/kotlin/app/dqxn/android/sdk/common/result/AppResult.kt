package app.dqxn.android.sdk.common.result

public sealed interface AppResult<out T> {
  public data class Ok<out T>(val value: T) : AppResult<T>

  public data class Err(val error: AppError) : AppResult<Nothing>

  public val isSuccess: Boolean
    get() = this is Ok

  public val isFailure: Boolean
    get() = this is Err

  public fun getOrNull(): T? =
    when (this) {
      is Ok -> value
      is Err -> null
    }

  public fun errorOrNull(): AppError? =
    when (this) {
      is Ok -> null
      is Err -> error
    }
}

public fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
  when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
  }

public fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
  when (this) {
    is AppResult.Ok -> transform(value)
    is AppResult.Err -> this
  }

public fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
  if (this is AppResult.Ok) block(value)
  return this
}

public fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
  if (this is AppResult.Err) block(error)
  return this
}

public fun <T> AppResult<T>.getOrElse(defaultValue: T): T =
  when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> defaultValue
  }

public fun <T> AppResult<T>.getOrElse(onFailure: (AppError) -> T): T =
  when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> onFailure(error)
  }
