package app.dqxn.android.sdk.contracts.entitlement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class GatedTest {

  private data class TestGated(
    override val requiredAnyEntitlement: Set<String>?,
  ) : Gated

  @Test
  fun `null entitlement list means free`() {
    val gated = TestGated(requiredAnyEntitlement = null)

    assertThat(gated.isAccessible { false }).isTrue()
  }

  @Test
  fun `empty set means free`() {
    val gated = TestGated(requiredAnyEntitlement = emptySet())

    assertThat(gated.isAccessible { false }).isTrue()
  }

  @Test
  fun `single required, user has it`() {
    val gated = TestGated(requiredAnyEntitlement = setOf("themes"))

    assertThat(gated.isAccessible { it == "themes" }).isTrue()
  }

  @Test
  fun `single required, user lacks it`() {
    val gated = TestGated(requiredAnyEntitlement = setOf("themes"))

    assertThat(gated.isAccessible { false }).isFalse()
  }

  @Test
  fun `OR logic - user has one of two`() {
    val gated = TestGated(requiredAnyEntitlement = setOf("themes", "plus"))

    assertThat(gated.isAccessible { it == "themes" }).isTrue()
  }

  @Test
  fun `OR logic - user has neither`() {
    val gated = TestGated(requiredAnyEntitlement = setOf("themes", "plus"))

    assertThat(gated.isAccessible { false }).isFalse()
  }
}
