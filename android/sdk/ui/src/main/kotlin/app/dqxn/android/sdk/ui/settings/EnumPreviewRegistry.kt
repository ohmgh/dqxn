package app.dqxn.android.sdk.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dagger.MapKey
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Hilt `@MapKey` for binding pack-contributed enum preview composables into [EnumPreviewRegistry].
 *
 * Usage in a pack module:
 * ```kotlin
 * @Module @InstallIn(SingletonComponent::class)
 * abstract class MyPackPreviewModule {
 *   companion object {
 *     @Provides @IntoMap @EnumPreviewKey(MyEnum::class)
 *     fun provideMyEnumPreview(): @Composable (Any) -> Unit = { value ->
 *       // custom preview rendering for MyEnum
 *     }
 *   }
 * }
 * ```
 */
@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class EnumPreviewKey(val value: KClass<out Enum<*>>)

/**
 * Registry for pack-contributed enum option preview composables.
 *
 * Packs contribute via Hilt `@IntoMap` with [EnumPreviewKey]. If no preview is registered for an
 * enum class, [Preview] falls back to a plain text label.
 */
public class EnumPreviewRegistry
@Inject
constructor(
  private val previews: Map<KClass<out Enum<*>>, @JvmSuppressWildcards @Composable (Any) -> Unit>,
) {
  /** Returns `true` if a custom preview composable is registered for [enumClass]. */
  public fun hasPreviews(enumClass: KClass<out Enum<*>>): Boolean = enumClass in previews

  /**
   * Renders a preview for the given enum [value]. If a custom preview is registered for the enum's
   * class, it is invoked. Otherwise, the enum's `name` is rendered as plain text.
   */
  @Composable
  public fun Preview(enumClass: KClass<out Enum<*>>, value: Enum<*>) {
    val previewComposable = previews[enumClass]
    if (previewComposable != null) {
      previewComposable(value)
    } else {
      Text(text = value.name)
    }
  }
}
