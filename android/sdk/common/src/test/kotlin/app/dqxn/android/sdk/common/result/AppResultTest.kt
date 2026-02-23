package app.dqxn.android.sdk.common.result

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class AppResultTest {

  private val networkError = AppError.Network("timeout")

  @Test
  fun `Ok value retrieval`() {
    val result: AppResult<Int> = AppResult.Ok(42)
    assertThat(result.getOrNull()).isEqualTo(42)
  }

  @Test
  fun `Err value is null`() {
    val result: AppResult<Int> = AppResult.Err(networkError)
    assertThat(result.getOrNull()).isNull()
  }

  @Test
  fun `map transforms Ok`() {
    val result = AppResult.Ok(2).map { it * 3 }
    assertThat(result).isEqualTo(AppResult.Ok(6))
  }

  @Test
  fun `map passes through Err`() {
    val err: AppResult<Int> = AppResult.Err(networkError)
    val result: AppResult<Int> = err.map { throw AssertionError("should not be called") }
    assertThat(result).isEqualTo(AppResult.Err(networkError))
  }

  @Test
  fun `flatMap chains Ok`() {
    val result = AppResult.Ok(2).flatMap { AppResult.Ok(it + 1) }
    assertThat(result).isEqualTo(AppResult.Ok(3))
  }

  @Test
  fun `flatMap short-circuits Err`() {
    val err: AppResult<Int> = AppResult.Err(networkError)
    val result: AppResult<Int> = err.flatMap { throw AssertionError("should not be called") }
    assertThat(result).isEqualTo(AppResult.Err(networkError))
  }

  @Test
  fun `getOrElse returns value for Ok`() {
    assertThat(AppResult.Ok(1).getOrElse(99)).isEqualTo(1)
  }

  @Test
  fun `getOrElse returns default for Err`() {
    val err: AppResult<Int> = AppResult.Err(networkError)
    assertThat(err.getOrElse(99)).isEqualTo(99)
  }

  @Test
  fun `getOrElse lambda receives error`() {
    val err: AppResult<String> = AppResult.Err(networkError)
    val result = err.getOrElse { it.message ?: "unknown" }
    assertThat(result).isEqualTo("timeout")
  }

  @Test
  fun `onSuccess invoked for Ok, not Err`() {
    var okCalled = false
    var errCalled = false

    AppResult.Ok(1).onSuccess { okCalled = true }
    val err: AppResult<Int> = AppResult.Err(networkError)
    err.onSuccess { errCalled = true }

    assertThat(okCalled).isTrue()
    assertThat(errCalled).isFalse()
  }

  @Test
  fun `onFailure invoked for Err, not Ok`() {
    var okCalled = false
    var errCalled = false

    AppResult.Ok(1).onFailure { okCalled = true }
    val err: AppResult<Int> = AppResult.Err(networkError)
    err.onFailure { errCalled = true }

    assertThat(okCalled).isFalse()
    assertThat(errCalled).isTrue()
  }

  @Test
  fun `isSuccess and isFailure properties`() {
    val ok: AppResult<Int> = AppResult.Ok(1)
    val err: AppResult<Int> = AppResult.Err(networkError)

    assertThat(ok.isSuccess).isTrue()
    assertThat(ok.isFailure).isFalse()
    assertThat(err.isSuccess).isFalse()
    assertThat(err.isFailure).isTrue()
  }

  @Test
  fun `errorOrNull returns error for Err, null for Ok`() {
    val ok: AppResult<Int> = AppResult.Ok(1)
    val err: AppResult<Int> = AppResult.Err(networkError)

    assertThat(ok.errorOrNull()).isNull()
    assertThat(err.errorOrNull()).isEqualTo(networkError)
  }

  @Test
  fun `onSuccess returns original result for chaining`() {
    val original: AppResult<Int> = AppResult.Ok(42)
    val returned = original.onSuccess { /* no-op */}
    assertThat(returned).isSameInstanceAs(original)
  }

  @Test
  fun `onFailure returns original result for chaining`() {
    val original: AppResult<Int> = AppResult.Err(networkError)
    val returned = original.onFailure { /* no-op */}
    assertThat(returned).isSameInstanceAs(original)
  }
}
