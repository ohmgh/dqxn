package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide

/**
 * Abstract property-based contract test for [WidgetRenderer]. JUnit5/jqwik.
 *
 * Verifies that renderers survive arbitrary settings maps without throwing. This is contract
 * test #9 from the spec, separated from [WidgetRendererContractTest] because jqwik requires JUnit5.
 */
public abstract class WidgetRendererPropertyTest {

  /** Create the renderer under test. */
  public abstract fun createRenderer(): WidgetRenderer

  @Property(tries = 50)
  public fun `renderer survives arbitrary settings`(
    @ForAll("settingsMaps") settings: Map<String, Any>,
  ) {
    val renderer = createRenderer()
    // Can't compose in jqwik, but verify accessibilityDescription and getDefaults don't throw
    renderer.accessibilityDescription(WidgetData.Empty)
    renderer.getDefaults(testWidgetContext())
  }

  @Provide
  public fun settingsMaps(): Arbitrary<Map<String, Any>> =
    Arbitraries.maps(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
        Arbitraries.oneOf(
          Arbitraries.integers().map { it as Any },
          Arbitraries.of(true, false).map { it as Any },
          Arbitraries.strings().ofMaxLength(20).map { it as Any },
          Arbitraries.floats().map { it as Any },
        ),
      )
      .ofMinSize(0)
      .ofMaxSize(10)
}
