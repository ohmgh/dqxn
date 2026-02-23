package app.dqxn.android.sdk.observability.crash

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrashEvidenceWriterTest {

  private lateinit var prefs: FakeSharedPreferences

  @BeforeEach
  fun setUp() {
    prefs = FakeSharedPreferences()
  }

  @Test
  fun `uncaughtException persists evidence to SharedPreferences`() {
    // Set a no-op delegate so the default handler doesn't interfere
    val saved = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
    try {
      val writer = CrashEvidenceWriter(prefs)
      val exception = RuntimeException("Widget [essentials:clock] crashed")

      writer.uncaughtException(Thread.currentThread(), exception)

      assertThat(prefs.getString(CrashEvidenceWriter.KEY_TYPE_ID, null))
        .isEqualTo("essentials:clock")
      assertThat(prefs.getString(CrashEvidenceWriter.KEY_EXCEPTION, null))
        .contains("RuntimeException")
      assertThat(prefs.getString(CrashEvidenceWriter.KEY_STACK_TOP5, null)).isNotEmpty()
      assertThat(prefs.getLong(CrashEvidenceWriter.KEY_TIMESTAMP, 0L)).isGreaterThan(0L)
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(saved)
    }
  }

  @Test
  fun `delegates to original handler`() {
    val delegateCalled = AtomicBoolean(false)
    val saved = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegateCalled.set(true) }
    try {
      val writer = CrashEvidenceWriter(prefs)
      writer.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

      assertThat(delegateCalled.get()).isTrue()
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(saved)
    }
  }

  @Test
  fun `extractWidgetTypeId finds typeId in nested cause chain`() {
    val rootCause = RuntimeException("Widget [essentials:clock] crashed")
    val wrapper = IllegalStateException("Rendering failed", rootCause)

    val typeId = CrashEvidenceWriter.extractWidgetTypeId(wrapper)
    assertThat(typeId).isEqualTo("essentials:clock")
  }

  @Test
  fun `extractWidgetTypeId returns null for non-widget exception`() {
    val exception = IllegalStateException("random error")
    val typeId = CrashEvidenceWriter.extractWidgetTypeId(exception)
    assertThat(typeId).isNull()
  }

  @Test
  fun `readLastCrash returns CrashEvidence`() {
    val saved = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
    try {
      val writer = CrashEvidenceWriter(prefs)
      val exception = RuntimeException("Widget [essentials:clock] crashed")
      writer.uncaughtException(Thread.currentThread(), exception)

      val evidence = writer.readLastCrash()
      assertThat(evidence).isNotNull()
      assertThat(evidence!!.typeId).isEqualTo("essentials:clock")
      assertThat(evidence.exception).contains("RuntimeException")
      assertThat(evidence.stackTop5).isNotEmpty()
      assertThat(evidence.timestamp).isGreaterThan(0L)
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(saved)
    }
  }

  @Test
  fun `uncaughtException handles prefs failure gracefully`() {
    val throwingPrefs = ThrowingSharedPreferences()
    val delegateCalled = AtomicBoolean(false)
    val saved = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegateCalled.set(true) }
    try {
      val writer = CrashEvidenceWriter(throwingPrefs)
      // Should NOT throw -- the exception in prefs.commit() is caught
      writer.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

      // Delegate should still be called despite prefs failure
      assertThat(delegateCalled.get()).isTrue()
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(saved)
    }
  }
}

/** In-memory SharedPreferences implementation for unit testing without Robolectric. */
private class FakeSharedPreferences : SharedPreferences {
  private val store = ConcurrentHashMap<String, Any?>()

  override fun getAll(): MutableMap<String, *> = store.toMutableMap()

  override fun getString(key: String?, defValue: String?): String? =
    store[key] as? String ?: defValue

  override fun getStringSet(
    key: String?,
    defValues: MutableSet<String>?,
  ): MutableSet<String>? {
    @Suppress("UNCHECKED_CAST") return store[key] as? MutableSet<String> ?: defValues
  }

  override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue

  override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue

  override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue

  override fun getBoolean(key: String?, defValue: Boolean): Boolean =
    store[key] as? Boolean ?: defValue

  override fun contains(key: String?): Boolean = store.containsKey(key)

  override fun edit(): SharedPreferences.Editor = FakeEditor(store)

  override fun registerOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?
  ) {}

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?
  ) {}
}

private class FakeEditor(
  private val store: ConcurrentHashMap<String, Any?>,
) : SharedPreferences.Editor {
  private val pending = mutableMapOf<String, Any?>()
  private val removals = mutableSetOf<String>()
  private var clearAll = false

  override fun putString(key: String?, value: String?): SharedPreferences.Editor {
    key?.let { pending[it] = value }
    return this
  }

  override fun putStringSet(
    key: String?,
    values: MutableSet<String>?,
  ): SharedPreferences.Editor {
    key?.let { pending[it] = values }
    return this
  }

  override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
    key?.let { pending[it] = value }
    return this
  }

  override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
    key?.let { pending[it] = value }
    return this
  }

  override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
    key?.let { pending[it] = value }
    return this
  }

  override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
    key?.let { pending[it] = value }
    return this
  }

  override fun remove(key: String?): SharedPreferences.Editor {
    key?.let { removals.add(it) }
    return this
  }

  override fun clear(): SharedPreferences.Editor {
    clearAll = true
    return this
  }

  override fun commit(): Boolean {
    applyChanges()
    return true
  }

  override fun apply() {
    applyChanges()
  }

  private fun applyChanges() {
    if (clearAll) store.clear()
    removals.forEach { store.remove(it) }
    store.putAll(pending)
  }
}

/** SharedPreferences stub that throws on edit().commit() to test error handling. */
private class ThrowingSharedPreferences : SharedPreferences {
  override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()

  override fun getString(key: String?, defValue: String?): String? = defValue

  override fun getStringSet(
    key: String?,
    defValues: MutableSet<String>?,
  ): MutableSet<String>? = defValues

  override fun getInt(key: String?, defValue: Int): Int = defValue

  override fun getLong(key: String?, defValue: Long): Long = defValue

  override fun getFloat(key: String?, defValue: Float): Float = defValue

  override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

  override fun contains(key: String?): Boolean = false

  override fun edit(): SharedPreferences.Editor = ThrowingEditor()

  override fun registerOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?
  ) {}

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?
  ) {}
}

private class ThrowingEditor : SharedPreferences.Editor {
  override fun putString(key: String?, value: String?): SharedPreferences.Editor = this

  override fun putStringSet(
    key: String?,
    values: MutableSet<String>?,
  ): SharedPreferences.Editor = this

  override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this

  override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this

  override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this

  override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this

  override fun remove(key: String?): SharedPreferences.Editor = this

  override fun clear(): SharedPreferences.Editor = this

  override fun commit(): Boolean = throw RuntimeException("Prefs failure!")

  override fun apply() = throw RuntimeException("Prefs failure!")
}
