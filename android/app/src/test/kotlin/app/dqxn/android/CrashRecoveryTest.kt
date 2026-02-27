package app.dqxn.android

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class CrashRecoveryTest {

  private lateinit var prefs: FakeSharedPreferences
  private lateinit var recovery: CrashRecovery

  @BeforeEach
  fun setUp() {
    prefs = FakeSharedPreferences()
    recovery = CrashRecovery(prefs)
  }

  @Test
  fun `3 crashes in window does not trigger safe mode`() {
    repeat(3) { recovery.recordCrash() }
    assertThat(recovery.isInSafeMode()).isFalse()
  }

  @Test
  fun `4 crashes in window triggers safe mode`() {
    repeat(4) { recovery.recordCrash() }
    assertThat(recovery.isInSafeMode()).isTrue()
  }

  @Test
  fun `crashes outside window do not count`() {
    // Record 3 crashes "in the past" by writing timestamps directly
    val now = System.currentTimeMillis()
    val oldTimestamps =
      listOf(
          now - 120_000L,
          now - 100_000L,
          now - 80_000L,
        )
        .joinToString(",")
    prefs.edit().putString(CrashRecovery.KEY_TIMESTAMPS, oldTimestamps).commit()

    // Record one recent crash
    recovery.recordCrash()

    assertThat(recovery.isInSafeMode()).isFalse()
  }

  @Test
  fun `boundary condition - crash at exactly 60001ms after first is excluded`() {
    val now = System.currentTimeMillis()
    // 3 crashes: first one is exactly 60_001ms old, two are recent
    val timestamps =
      listOf(
          now - 60_001L,
          now - 1000L,
          now - 500L,
        )
        .joinToString(",")
    prefs.edit().putString(CrashRecovery.KEY_TIMESTAMPS, timestamps).commit()

    // Record one more recent crash -- total recent should be 3 (the old one is excluded)
    recovery.recordCrash()

    assertThat(recovery.isInSafeMode()).isFalse()
  }

  @Test
  fun `clearCrashHistory resets safe mode`() {
    repeat(5) { recovery.recordCrash() }
    assertThat(recovery.isInSafeMode()).isTrue()

    recovery.clearCrashHistory()

    assertThat(recovery.isInSafeMode()).isFalse()
  }

  @Test
  fun `empty prefs returns not in safe mode`() {
    assertThat(recovery.isInSafeMode()).isFalse()
  }

  @Test
  fun `corrupted prefs string gracefully returns false`() {
    prefs.edit().putString(CrashRecovery.KEY_TIMESTAMPS, "garbage,data,not,numbers").commit()
    assertThat(recovery.isInSafeMode()).isFalse()
  }

  /**
   * Minimal in-memory [SharedPreferences] for unit testing without Robolectric. Only implements
   * methods used by [CrashRecovery].
   */
  private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
      data[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
      @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
      data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data, listeners)

    override fun registerOnSharedPreferenceChangeListener(
      listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
      listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
      listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
      listener?.let { listeners.remove(it) }
    }

    private class FakeEditor(
      private val data: MutableMap<String, Any?>,
      private val listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>,
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
        if (clearAll) data.clear()
        removals.forEach { data.remove(it) }
        data.putAll(pending)
      }
    }
  }
}
