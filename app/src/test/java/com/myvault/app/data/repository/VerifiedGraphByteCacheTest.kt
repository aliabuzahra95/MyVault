package com.myvault.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class VerifiedGraphByteCacheTest {
    private fun ref(id: String, bytes: ByteArray) = GraphObjectRef(id, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
    @Test fun onlyVerifiedBytesEnterCacheAndReturnedBytesCannotMutateIt() {
        val cache = VerifiedGraphByteCache(); val bytes = "verified".toByteArray(); val ref = ref("object", bytes)
        cache.put("scope", ref, bytes); bytes[0] = 0
        val fetched = cache.get("scope", ref)!!; fetched[0] = 0
        assertEquals("verified", cache.get("scope", ref)!!.toString(Charsets.UTF_8))
        try { cache.put("scope", ref, "invalid".toByteArray()); fail("Invalid bytes must not be cached") }
        catch (_: IllegalStateException) { }
        assertEquals("verified", cache.get("scope", ref)!!.toString(Charsets.UTF_8))
    }
    @Test fun accountLineageNamespaceAndExactDescriptorAreAllScoped() {
        val cache = VerifiedGraphByteCache(); val bytes = "verified".toByteArray(); val ref = ref("object", bytes)
        cache.put("account-a:lineage-a:root-a", ref, bytes)
        for (scope in listOf("account-b:lineage-a:root-a", "account-a:lineage-b:root-a", "account-a:lineage-a:root-b"))
            assertNull(cache.get(scope, ref))
        assertNull(cache.get("account-a:lineage-a:root-a", ref.copy(sha256 = "a".repeat(64))))
        assertNull(cache.get("account-a:lineage-a:root-a", ref.copy(size = 99)))
    }
    @Test fun evictionAndCacheLossOnlyCauseMisses() {
        val cache = VerifiedGraphByteCache(byteLimit = 8, entryLimit = 8)
        val a = "1234".toByteArray(); val b = "5678".toByteArray(); val c = "abcd".toByteArray()
        cache.put("scope", ref("a", a), a); cache.put("scope", ref("b", b), b)
        assertNotNull(cache.get("scope", ref("a", a)))
        cache.put("scope", ref("c", c), c)
        assertNull(cache.get("scope", ref("b", b))); assertNotNull(cache.get("scope", ref("a", a)))
        cache.clear(); assertNull(cache.get("scope", ref("a", a)))
        val huge = ByteArray(9); cache.put("scope", ref("huge", huge), huge)
        assertNull(cache.get("scope", ref("huge", huge)))
    }
    @Test fun corruptedMemoryEntryIsDiscardedRatherThanTrusted() {
        val cache = VerifiedGraphByteCache(); val bytes = "verified".toByteArray(); val ref = ref("object", bytes)
        cache.put("scope", ref, bytes)
        val field = cache.javaClass.getDeclaredField("entries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val values = field.get(cache) as Map<Any, ByteArray>
        values.values.single()[0] = 0
        assertNull(cache.get("scope", ref)); assertEquals(0L, cache.hits)
        cache.put("scope", ref, bytes); assertArrayEquals(bytes, cache.get("scope", ref))
    }
}
