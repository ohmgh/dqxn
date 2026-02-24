package app.dqxn.android

import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class StubEntitlementManagerTest {

  private val manager = StubEntitlementManager()

  @Test
  fun `hasEntitlement free returns true`() {
    assertThat(manager.hasEntitlement("free")).isTrue()
  }

  @Test
  fun `hasEntitlement plus returns false`() {
    assertThat(manager.hasEntitlement("plus")).isFalse()
  }

  @Test
  fun `hasEntitlement themes returns false`() {
    assertThat(manager.hasEntitlement("themes")).isFalse()
  }

  @Test
  fun `getActiveEntitlements returns only free`() {
    assertThat(manager.getActiveEntitlements()).containsExactly("free")
  }

  @Test
  fun `implements EntitlementManager interface`() {
    val entitlementManager: EntitlementManager = manager
    assertThat(entitlementManager).isNotNull()
  }
}
