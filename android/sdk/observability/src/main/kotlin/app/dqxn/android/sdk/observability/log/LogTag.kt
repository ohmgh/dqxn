package app.dqxn.android.sdk.observability.log

/**
 * Type-safe log tag. Packs define their own tags freely -- no registration required.
 */
@JvmInline
public value class LogTag(public val value: String)
