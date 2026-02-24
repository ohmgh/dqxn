package app.dqxn.android.core.agentic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class SemanticsOwnerHolderTest {

  @Test
  fun `snapshot returns null when no owner registered`() {
    val holder = SemanticsOwnerHolder()

    assertThat(holder.snapshot()).isNull()
  }

  @Test
  fun `query returns empty list when no owner registered`() {
    val holder = SemanticsOwnerHolder()

    val result = holder.query(SemanticsFilter(testTag = "anything"))

    assertThat(result).isEmpty()
  }

  @Test
  fun `register with non-SemanticsOwner type is safe no-op`() {
    val holder = SemanticsOwnerHolder()

    // Registering a non-SemanticsOwner should not throw
    holder.register("not a semantics owner")

    // Should still return null since the type check failed
    assertThat(holder.snapshot()).isNull()
  }

  @Test
  fun `unregister clears reference - snapshot returns null after`() {
    val holder = SemanticsOwnerHolder()

    // Register something (even though it's not a real SemanticsOwner, unregister should still work)
    holder.register("placeholder")
    holder.unregister()

    assertThat(holder.snapshot()).isNull()
  }

  @Test
  fun `multiple register-unregister cycles are safe`() {
    val holder = SemanticsOwnerHolder()

    repeat(10) {
      holder.register("owner-$it")
      holder.unregister()
    }

    assertThat(holder.snapshot()).isNull()
  }

  @Test
  fun `query with empty filter returns empty when no owner`() {
    val holder = SemanticsOwnerHolder()

    val result = holder.query(SemanticsFilter())

    assertThat(result).isEmpty()
  }
}
