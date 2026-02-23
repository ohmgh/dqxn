package app.dqxn.android.sdk.contracts.fault

import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ProviderFaultTest {

  @Test
  fun `Kill is a singleton`() {
    val a = ProviderFault.Kill
    val b = ProviderFault.Kill

    assertThat(a).isSameInstanceAs(b)
    assertThat(a).isInstanceOf(ProviderFault::class.java)
  }

  @Test
  fun `Delay holds delayMs`() {
    val fault = ProviderFault.Delay(500L)

    assertThat(fault.delayMs).isEqualTo(500L)
  }

  @Test
  fun `Error holds exception`() {
    val exception = RuntimeException("boom")
    val fault = ProviderFault.Error(exception)

    assertThat(fault.exception).isSameInstanceAs(exception)
    assertThat(fault.exception.message).isEqualTo("boom")
  }

  @Test
  fun `ErrorOnNext holds exception`() {
    val exception = IllegalStateException("next error")
    val fault = ProviderFault.ErrorOnNext(exception)

    assertThat(fault.exception).isSameInstanceAs(exception)
    assertThat(fault.exception.message).isEqualTo("next error")
  }

  @Test
  fun `Corrupt holds transform function`() {
    val transform: (DataSnapshot) -> DataSnapshot = { it }
    val fault = ProviderFault.Corrupt(transform)

    assertThat(fault.transform).isSameInstanceAs(transform)
  }

  @Test
  fun `Flap holds timing`() {
    val fault = ProviderFault.Flap(onMillis = 100, offMillis = 200)

    assertThat(fault.onMillis).isEqualTo(100)
    assertThat(fault.offMillis).isEqualTo(200)
  }

  @Test
  fun `Stall is a singleton`() {
    val a = ProviderFault.Stall
    val b = ProviderFault.Stall

    assertThat(a).isSameInstanceAs(b)
    assertThat(a).isInstanceOf(ProviderFault::class.java)
  }
}
