package app.dqxn.android.sdk.observability.crash

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrashEvidenceWriterTest {

  private val store = ConcurrentHashMap<String, Any?>()
  private lateinit var editor: SharedPreferences.Editor
  private lateinit var prefs: SharedPreferences
  private var savedHandler: Thread.UncaughtExceptionHandler? = null

  @BeforeEach
  fun setUp() {
    savedHandler = Thread.getDefaultUncaughtExceptionHandler()

    editor = mockk<SharedPreferences.Editor> {
      every { putString(any(), any()) } answers {
        store[firstArg()] = secondArg<String?>()
        this@mockk
      }
      every { putLong(any(), any()) } answers {
        store[firstArg()] = secondArg<Long>()
        this@mockk
      }
      every { remove(any()) } answers {
        store.remove(firstArg<String>())
        this@mockk
      }
      every { commit() } returns true
      every { apply() } answers {}
    }

    prefs = mockk<SharedPreferences> {
      every { edit() } returns editor
      every { getString(any(), any()) } answers { store[firstArg()] as? String ?: secondArg() }
      every { getLong(any(), any()) } answers { store[firstArg()] as? Long ?: secondArg() }
    }
  }

  @AfterEach
  fun tearDown() {
    Thread.setDefaultUncaughtExceptionHandler(savedHandler)
  }

  @Test
  fun `uncaughtException persists evidence to SharedPreferences`() {
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
    val writer = CrashEvidenceWriter(prefs)

    writer.uncaughtException(Thread.currentThread(), RuntimeException("Widget [essentials:clock] crashed"))

    assertThat(store[CrashEvidenceWriter.KEY_TYPE_ID]).isEqualTo("essentials:clock")
    assertThat(store[CrashEvidenceWriter.KEY_EXCEPTION] as String).contains("RuntimeException")
    assertThat(store[CrashEvidenceWriter.KEY_STACK_TOP5] as String).isNotEmpty()
    assertThat(store[CrashEvidenceWriter.KEY_TIMESTAMP] as Long).isGreaterThan(0L)
    verify { editor.commit() }
  }

  @Test
  fun `delegates to original handler`() {
    val delegateCalled = AtomicBoolean(false)
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegateCalled.set(true) }
    val writer = CrashEvidenceWriter(prefs)

    writer.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

    assertThat(delegateCalled.get()).isTrue()
  }

  @Test
  fun `extractWidgetTypeId finds typeId in nested cause chain`() {
    val rootCause = RuntimeException("Widget [essentials:clock] crashed")
    val wrapper = IllegalStateException("Rendering failed", rootCause)

    assertThat(CrashEvidenceWriter.extractWidgetTypeId(wrapper)).isEqualTo("essentials:clock")
  }

  @Test
  fun `extractWidgetTypeId returns null for non-widget exception`() {
    assertThat(CrashEvidenceWriter.extractWidgetTypeId(IllegalStateException("random error"))).isNull()
  }

  @Test
  fun `readLastCrash returns CrashEvidence`() {
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
    val writer = CrashEvidenceWriter(prefs)

    writer.uncaughtException(Thread.currentThread(), RuntimeException("Widget [essentials:clock] crashed"))
    val evidence = writer.readLastCrash()

    assertThat(evidence).isNotNull()
    assertThat(evidence!!.typeId).isEqualTo("essentials:clock")
    assertThat(evidence.exception).contains("RuntimeException")
    assertThat(evidence.stackTop5).isNotEmpty()
    assertThat(evidence.timestamp).isGreaterThan(0L)
  }

  @Test
  fun `uncaughtException handles prefs failure gracefully`() {
    val throwingEditor = mockk<SharedPreferences.Editor> {
      every { putString(any(), any()) } returns this
      every { putLong(any(), any()) } returns this
      every { commit() } throws RuntimeException("Prefs failure!")
    }
    val throwingPrefs = mockk<SharedPreferences> {
      every { edit() } returns throwingEditor
    }

    val delegateCalled = AtomicBoolean(false)
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegateCalled.set(true) }
    val writer = CrashEvidenceWriter(throwingPrefs)

    writer.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

    assertThat(delegateCalled.get()).isTrue()
  }
}
