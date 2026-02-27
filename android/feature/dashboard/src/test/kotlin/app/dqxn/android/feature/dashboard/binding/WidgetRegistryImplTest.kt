package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.sdk.contracts.testing.TestWidgetRenderer
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogLevel
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.ImmutableMap
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class WidgetRegistryImplTest {

  private val logger = NoOpLogger

  @Test
  fun `findByTypeId returns renderer when present`() {
    val renderer = TestWidgetRenderer(typeId = "essentials:clock")
    val registry = WidgetRegistryImpl(setOf(renderer), logger)

    val found = registry.findByTypeId("essentials:clock")

    assertThat(found).isNotNull()
    assertThat(found!!.typeId).isEqualTo("essentials:clock")
  }

  @Test
  fun `findByTypeId returns null for unknown type`() {
    val renderer = TestWidgetRenderer(typeId = "essentials:clock")
    val registry = WidgetRegistryImpl(setOf(renderer), logger)

    val found = registry.findByTypeId("essentials:nonexistent")

    assertThat(found).isNull()
  }

  @Test
  fun `empty set produces empty registry`() {
    val registry = WidgetRegistryImpl(emptySet(), logger)

    assertThat(registry.getAll()).isEmpty()
    assertThat(registry.getTypeIds()).isEmpty()
    assertThat(registry.findByTypeId("anything")).isNull()
  }

  @Test
  fun `duplicate typeId last-write-wins with warning log`() {
    val warnings = mutableListOf<String>()
    val trackingLogger =
      object : DqxnLogger {
        override fun isEnabled(level: LogLevel, tag: LogTag): Boolean = true

        override fun log(
          level: LogLevel,
          tag: LogTag,
          message: String,
          throwable: Throwable?,
          traceId: String?,
          spanId: String?,
          fields: ImmutableMap<String, Any>,
        ) {
          if (level == LogLevel.WARN) warnings.add(message)
        }
      }

    val renderer1 = TestWidgetRenderer(typeId = "essentials:clock", displayName = "Clock v1")
    val renderer2 = TestWidgetRenderer(typeId = "essentials:clock", displayName = "Clock v2")

    // Use a LinkedHashSet to guarantee insertion order: renderer1 first, renderer2 second
    val registry = WidgetRegistryImpl(linkedSetOf(renderer1, renderer2), trackingLogger)

    // Last-write-wins: renderer2 should be in the registry
    val found = registry.findByTypeId("essentials:clock")
    assertThat(found).isNotNull()
    assertThat(found!!.displayName).isEqualTo("Clock v2")

    // Should have logged a warning about the duplicate
    assertThat(warnings).hasSize(1)
    assertThat(warnings[0]).contains("Duplicate typeId")
  }

  @Test
  fun `getAll returns all registered renderers`() {
    val renderers =
      setOf(
        TestWidgetRenderer(typeId = "essentials:clock"),
        TestWidgetRenderer(typeId = "essentials:battery"),
        TestWidgetRenderer(typeId = "essentials:compass"),
      )
    val registry = WidgetRegistryImpl(renderers, logger)

    assertThat(registry.getAll()).hasSize(3)
    assertThat(registry.getTypeIds())
      .containsExactly(
        "essentials:clock",
        "essentials:battery",
        "essentials:compass",
      )
  }
}
