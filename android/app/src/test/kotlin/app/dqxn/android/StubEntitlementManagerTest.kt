package app.dqxn.android

import android.content.SharedPreferences
import app.cash.turbine.test
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class StubEntitlementManagerTest {

  private val store = mutableMapOf<String, Set<String>>()
  private val editor = mockk<SharedPreferences.Editor>(relaxed = true) {
    every { putStringSet(any(), any()) } answers {
      store[firstArg()] = secondArg()
      this@mockk
    }
  }
  private val prefs = mockk<SharedPreferences> {
    every { getStringSet(any(), any()) } answers { store[firstArg()] }
    every { edit() } returns editor
  }
  private val manager = StubEntitlementManager(prefs)

  @Test
  fun `initial state has free entitlement only`() {
    assertThat(manager.getActiveEntitlements()).containsExactly("free")
  }

  @Test
  fun `hasEntitlement returns true for free`() {
    assertThat(manager.hasEntitlement("free")).isTrue()
  }

  @Test
  fun `hasEntitlement returns false for themes`() {
    assertThat(manager.hasEntitlement("themes")).isFalse()
  }

  @Test
  fun `simulateGrant adds entitlement and emits via flow`() = runTest {
    manager.entitlementChanges.test {
      assertThat(awaitItem()).containsExactly("free")
      manager.simulateGrant("themes")
      assertThat(awaitItem()).containsExactly("free", "themes")
    }
  }

  @Test
  fun `simulateRevocation removes entitlement and emits via flow`() = runTest {
    manager.simulateGrant("plus")
    manager.entitlementChanges.test {
      assertThat(awaitItem()).containsExactly("free", "plus")
      manager.simulateRevocation("plus")
      assertThat(awaitItem()).containsExactly("free")
    }
  }

  @Test
  fun `simulateRevocation of nonexistent entitlement is no-op`() {
    manager.simulateRevocation("nonexistent")
    assertThat(manager.getActiveEntitlements()).containsExactly("free")
  }

  @Test
  fun `simulateGrant then simulateRevocation returns to previous state`() {
    manager.simulateGrant("themes")
    assertThat(manager.hasEntitlement("themes")).isTrue()
    manager.simulateRevocation("themes")
    assertThat(manager.hasEntitlement("themes")).isFalse()
    assertThat(manager.getActiveEntitlements()).containsExactly("free")
  }

  @Test
  fun `entitlementChanges flow emits initial state on collection`() = runTest {
    manager.entitlementChanges.test {
      assertThat(awaitItem()).containsExactly("free")
      cancel()
    }
  }

  @Test
  fun `reset returns to free-only state`() = runTest {
    manager.simulateGrant("plus")
    manager.simulateGrant("themes")
    assertThat(manager.getActiveEntitlements()).containsExactly("free", "plus", "themes")

    manager.reset()
    assertThat(manager.getActiveEntitlements()).containsExactly("free")
  }

  @Test
  fun `multiple sequential grants accumulate correctly`() {
    manager.simulateGrant("plus")
    manager.simulateGrant("themes")
    manager.simulateGrant("premium")
    assertThat(manager.getActiveEntitlements()).containsExactly("free", "plus", "themes", "premium")
  }

  @Test
  fun `implements EntitlementManager interface`() {
    val entitlementManager: EntitlementManager = manager
    assertThat(entitlementManager).isNotNull()
  }

  @Test
  fun `simulateGrant persists to SharedPreferences`() {
    manager.simulateGrant("themes")
    assertThat(store["stub_entitlements"]).containsExactly("free", "themes")
  }

  @Test
  fun `new instance restores persisted entitlements`() {
    manager.simulateGrant("themes")
    manager.simulateGrant("plus")

    val restored = StubEntitlementManager(prefs)
    assertThat(restored.getActiveEntitlements()).containsExactly("free", "themes", "plus")
  }

  @Test
  fun `reset clears persisted entitlements`() {
    manager.simulateGrant("themes")
    manager.reset()

    val restored = StubEntitlementManager(prefs)
    assertThat(restored.getActiveEntitlements()).containsExactly("free")
  }
}
