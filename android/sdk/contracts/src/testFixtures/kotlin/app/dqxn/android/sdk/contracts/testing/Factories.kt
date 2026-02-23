package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import kotlin.reflect.KClass
import kotlinx.collections.immutable.toPersistentMap

/** Creates [WidgetData] from snapshot pairs for test assertions. */
public fun testWidgetData(
  vararg snapshots: Pair<KClass<out DataSnapshot>, DataSnapshot>,
): WidgetData =
  WidgetData(
    snapshots = snapshots.toMap().toPersistentMap(),
    timestamp = snapshots.maxOfOrNull { it.second.timestamp } ?: 0L,
  )

/** Returns [WidgetStyle.Default] for contract tests that don't need custom styling. */
public fun testWidgetStyle(): WidgetStyle = WidgetStyle.Default

/** Returns [WidgetContext.DEFAULT] for contract tests. */
public fun testWidgetContext(): WidgetContext = WidgetContext.DEFAULT
