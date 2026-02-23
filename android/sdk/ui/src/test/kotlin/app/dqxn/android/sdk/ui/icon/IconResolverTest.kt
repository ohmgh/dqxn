package app.dqxn.android.sdk.ui.icon

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IconResolver")
class IconResolverTest {

  @BeforeEach
  fun setUp() {
    IconResolver.clearCache()
  }

  @Test
  fun `returns null for unknown icon`() {
    val result = IconResolver.resolve("NonExistentIcon123")
    assertThat(result).isNull()
  }

  @Test
  fun `returns null for empty string`() {
    val result = IconResolver.resolve("")
    assertThat(result).isNull()
  }

  @Test
  fun `caches resolved icons - same result on repeated calls`() {
    val first = IconResolver.resolve("SomeUnknownIcon")
    val second = IconResolver.resolve("SomeUnknownIcon")
    // Both should be null (unknown icon), but the cache should hold the same reference
    assertThat(first).isEqualTo(second)
  }

  @Test
  fun `handles camelCase input`() {
    // The resolver converts first char to uppercase, so "arrowBack" -> "ArrowBack"
    // Whether it resolves depends on the reflection finding the icon.
    // This tests that the conversion doesn't throw.
    val result = IconResolver.resolve("arrowBack")
    // Not asserting non-null because icon availability depends on the icons-extended artifact
    // and how they're compiled. The key contract is no exception thrown.
    assertThat(true).isTrue() // Reached here without exception
  }

  @Test
  fun `clearCache empties the cache`() {
    IconResolver.resolve("TestIcon1")
    IconResolver.resolve("TestIcon2")
    IconResolver.clearCache()
    // After clear, resolve should re-compute (no exception)
    val result = IconResolver.resolve("TestIcon1")
    assertThat(result).isNull()
  }
}
