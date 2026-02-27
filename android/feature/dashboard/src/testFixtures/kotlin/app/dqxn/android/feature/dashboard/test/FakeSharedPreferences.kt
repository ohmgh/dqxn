package app.dqxn.android.feature.dashboard.test

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] for unit tests. Avoids Robolectric dependency. Both [apply] and
 * [commit] are immediate (synchronous). Follows the same pattern as CrashRecoveryTest in `:app`.
 */
public class FakeSharedPreferences : SharedPreferences {

  private val data = mutableMapOf<String, Any?>()
  private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

  override fun getAll(): MutableMap<String, *> = data.toMutableMap()

  override fun getString(key: String?, defValue: String?): String? =
    data[key] as? String ?: defValue

  override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
    @Suppress("UNCHECKED_CAST") return data[key] as? MutableSet<String> ?: defValues
  }

  override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue

  override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue

  override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue

  override fun getBoolean(key: String?, defValue: Boolean): Boolean =
    data[key] as? Boolean ?: defValue

  override fun contains(key: String?): Boolean = data.containsKey(key)

  override fun edit(): SharedPreferences.Editor = FakeEditor()

  override fun registerOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?
  ) {
    listener?.let { listeners.add(it) }
  }

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?
  ) {
    listener?.let { listeners.remove(it) }
  }

  private inner class FakeEditor : SharedPreferences.Editor {
    private val edits = mutableMapOf<String, Any?>()
    private val removals = mutableSetOf<String>()
    private var clear = false

    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
      key?.let { edits[it] = value }
      return this
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
      key?.let { edits[it] = values }
      return this
    }

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
      key?.let { edits[it] = value }
      return this
    }

    override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
      key?.let { edits[it] = value }
      return this
    }

    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
      key?.let { edits[it] = value }
      return this
    }

    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
      key?.let { edits[it] = value }
      return this
    }

    override fun remove(key: String?): SharedPreferences.Editor {
      key?.let { removals.add(it) }
      return this
    }

    override fun clear(): SharedPreferences.Editor {
      clear = true
      return this
    }

    override fun commit(): Boolean {
      applyEdits()
      return true
    }

    override fun apply() {
      applyEdits()
    }

    private fun applyEdits() {
      if (clear) data.clear()
      removals.forEach { data.remove(it) }
      edits.forEach { (key, value) -> data[key] = value }
      val changedKeys = edits.keys + removals
      changedKeys.forEach { key ->
        listeners.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
      }
    }
  }
}
