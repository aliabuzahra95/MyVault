package com.myvault.app.data.repository

/** Bounded, process-local immutable bytes; never a graph head or an existence receipt. */
internal class VerifiedGraphByteCache(private val byteLimit: Int = 32 * 1024 * 1024,
    val entryLimit: Int = 8 * 1024 * 1024) {
    private data class Key(val scope: String, val ref: GraphObjectRef)
    private val entries = LinkedHashMap<Key, ByteArray>(16, 0.75f, true)
    private var bytes = 0
    var hits: Long = 0; private set
    var misses: Long = 0; private set
    var hashedBytes: Long = 0; private set

    @Synchronized fun get(scope: String, ref: GraphObjectRef): ByteArray? {
        val key = Key(scope, ref)
        val value = entries[key] ?: run { misses++; return null }
        try {
            hashedBytes += value.size
            BackupGraphProtocol.verify(ref, value)
        } catch (_: IllegalStateException) {
            entries.remove(key); bytes -= value.size; misses++; return null
        }
        hits++
        return value.copyOf()
    }

    @Synchronized fun put(scope: String, ref: GraphObjectRef, value: ByteArray) {
        hashedBytes += value.size
        BackupGraphProtocol.verify(ref, value)
        if (value.size > entryLimit || value.size > byteLimit) return
        val key = Key(scope, ref)
        entries.remove(key)?.let { bytes -= it.size }
        entries[key] = value.copyOf(); bytes += value.size
        while (bytes > byteLimit) {
            val first = entries.entries.iterator()
            val item = first.next(); bytes -= item.value.size; first.remove()
        }
    }

    @Synchronized fun clear() { entries.clear(); bytes = 0 }
}

internal val processVerifiedGraphBytes = VerifiedGraphByteCache()
