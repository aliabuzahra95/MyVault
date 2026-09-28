package com.myvault.app.data.repository

/** Internal opt-in diagnostics. No account, payload, credential or wall-clock data is recorded. */
internal class BackupGraphTiming(val enabled: Boolean = false) {
    data class Span(val name: String, val startNanos: Long, val endNanos: Long)
    private val spans = mutableListOf<Span>()
    private val active = mutableListOf<String>()
    val current: String? get() = synchronized(active) { active.lastOrNull() }
    fun snapshot(): List<Span> = synchronized(spans) { spans.toList() }
    suspend fun <T> measure(name: String, work: suspend () -> T): T {
        if (!enabled) return work()
        val start = System.nanoTime()
        synchronized(active) { active.add(name) }
        try { return work() } finally {
            synchronized(active) { check(active.removeAt(active.lastIndex) == name) }
            synchronized(spans) { spans.add(Span(name, start, System.nanoTime())) }
        }
    }
    fun <T> local(name: String, work: () -> T): T {
        if (!enabled) return work()
        val start = System.nanoTime()
        synchronized(active) { active.add(name) }
        try { return work() } finally {
            synchronized(active) { check(active.removeAt(active.lastIndex) == name) }
            synchronized(spans) { spans.add(Span(name, start, System.nanoTime())) }
        }
    }
}
