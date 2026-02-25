package app.dqxn.android.feature.settings.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.data.device.PairedDevice
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.contracts.setup.SetupResult
import app.dqxn.android.sdk.contracts.setup.isRequirement
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fullscreen paginated setup flow (NOT a bottom sheet despite the name).
 *
 * Assembles [SetupDefinitionRenderer] cards into a multi-page wizard with:
 * - [AnimatedContent] directional transitions (slide left/right based on navigation direction)
 * - Forward gating: only requirement types ([isRequirement]) block navigation; Setting/Info/
 *   Instruction types always satisfy
 * - **evaluationTrigger** counter pattern: [LifecycleResumeEffect] increments a counter on resume,
 *   forcing [LaunchedEffect] to re-evaluate permissions/services after returning from system settings
 * - **Two exclusive [BackHandler]s** (Pitfall 5): one for page-back (currentPage > 0), one for
 *   dismiss (currentPage == 0). Unification breaks exclusivity.
 * - Buttons are alpha-dimmed (50%) when gated but remain tappable (Pitfall 6 -- do NOT use
 *   `enabled = false`)
 *
 * Settings are loaded once via [produceState] from [ProviderSettingsStore.getAllSettings], mutated
 * in-memory, and written through immediately on change (no debounce, no transaction boundary).
 */
@Composable
fun SetupSheet(
  setupSchema: List<SetupPageDefinition>,
  packId: String,
  providerId: String,
  providerSettingsStore: ProviderSettingsStore,
  pairedDeviceStore: PairedDeviceStore,
  evaluator: SetupEvaluatorImpl,
  entitlementManager: EntitlementManager,
  onComplete: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var currentPage by remember { mutableIntStateOf(0) }
  val totalPages = setupSchema.size
  val scope = rememberCoroutineScope()

  // --- Settings loading ---
  // One-shot load from store, then local in-memory mutations with immediate write-through.
  val initialSettings by produceState<Map<String, Any?>>(emptyMap(), packId, providerId) {
    value = providerSettingsStore.getAllSettings(packId, providerId).first()
  }
  val currentSettings = remember { mutableStateMapOf<String, Any?>() }
  LaunchedEffect(initialSettings) {
    if (initialSettings.isNotEmpty() && currentSettings.isEmpty()) {
      currentSettings.putAll(initialSettings)
    }
  }

  // --- Paired devices snapshot for persistence-aware evaluation ---
  val pairedDevices by produceState<ImmutableList<PairedDevice>>(
    initialValue = persistentListOf(),
    pairedDeviceStore,
  ) {
    pairedDeviceStore.devices.collect { value = it }
  }

  // --- evaluationTrigger counter pattern ---
  // Without counter, LaunchedEffect won't re-run after permission grants because the
  // providerId key hasn't changed. LifecycleResumeEffect increments on every resume
  // (returning from system settings, completing BLE pairing, etc.).
  var evaluationTrigger by remember { mutableIntStateOf(0) }
  LifecycleResumeEffect(Unit) {
    evaluationTrigger++
    onPauseOrDispose {}
  }

  var satisfiedDefinitions by remember { mutableStateOf(emptySet<String>()) }
  LaunchedEffect(providerId, evaluationTrigger) {
    satisfiedDefinitions = evaluator.evaluateWithPersistence(
      schema = setupSchema,
      pairedDevices = pairedDevices,
    ).filter { it.satisfied }.map { it.definitionId }.toSet()
  }

  // --- Forward navigation gating ---
  // Only requirement types block; Setting/Info/Instruction always satisfy.
  val isCurrentPageSatisfied = remember(currentPage, satisfiedDefinitions) {
    if (currentPage >= totalPages) return@remember true
    val page = setupSchema[currentPage]
    page.definitions.all { definition ->
      if (definition.isRequirement) {
        definition.id in satisfiedDefinitions
      } else {
        true // Setting, Info, Instruction always satisfy
      }
    }
  }

  // --- Two exclusive BackHandlers (Pitfall 5) ---
  // Don't unify -- exclusivity is load-bearing. Android dispatches to the last-registered
  // enabled BackHandler, so ordering matters.
  BackHandler(enabled = currentPage > 0) {
    currentPage--
  }
  BackHandler(enabled = currentPage == 0) {
    onDismiss()
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .testTag("setup_sheet"),
  ) {
    // Page content with directional AnimatedContent transitions
    AnimatedContent(
      targetState = currentPage,
      transitionSpec = {
        val direction = if (targetState > initialState) 1 else -1
        slideInHorizontally { fullWidth -> direction * fullWidth } togetherWith
          slideOutHorizontally { fullWidth -> -direction * fullWidth }
      },
      label = "SetupPageTransition",
      modifier = Modifier.weight(1f),
    ) { page ->
      if (page < totalPages) {
        SetupPageContent(
          page = setupSchema[page],
          satisfiedDefinitions = satisfiedDefinitions,
          currentSettings = currentSettings,
          entitlementManager = entitlementManager,
          packId = packId,
          providerId = providerId,
          providerSettingsStore = providerSettingsStore,
          writeScope = scope,
        )
      }
    }

    // Navigation bar
    SetupNavigationBar(
      currentPage = currentPage,
      totalPages = totalPages,
      isPageSatisfied = isCurrentPageSatisfied,
      onBack = { currentPage-- },
      onNext = { currentPage++ },
      onDone = onComplete,
    )
  }
}

/**
 * Renders a single page of setup definitions in a scrollable column.
 */
@Composable
private fun SetupPageContent(
  page: SetupPageDefinition,
  satisfiedDefinitions: Set<String>,
  currentSettings: MutableMap<String, Any?>,
  entitlementManager: EntitlementManager,
  packId: String,
  providerId: String,
  providerSettingsStore: ProviderSettingsStore,
  writeScope: CoroutineScope,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = DashboardSpacing.ScreenEdgePadding),
  ) {
    Spacer(modifier = Modifier.height(DashboardSpacing.SpaceM))

    page.definitions.forEach { definition ->
      SetupDefinitionRenderer(
        definition = definition,
        result = SetupResult(
          definitionId = definition.id,
          satisfied = definition.id in satisfiedDefinitions,
        ),
        currentSettings = currentSettings,
        entitlementManager = entitlementManager,
        onValueChanged = { key, value ->
          currentSettings[key] = value
          // Immediate write-through on Compose scope (no debounce)
          writeScope.launch {
            providerSettingsStore.setSetting(packId, providerId, key, value)
          }
        },
        onPermissionRequest = { /* Permission requests handled by parent Activity */ },
        onSystemSettingsOpen = { /* System settings intent handled by parent Activity */ },
      )

      Spacer(modifier = Modifier.height(DashboardSpacing.ItemGap))
    }
  }
}
