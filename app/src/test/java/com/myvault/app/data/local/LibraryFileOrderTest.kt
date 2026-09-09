package com.myvault.app.data.local

import org.junit.Assert.*
import org.junit.Test

class LibraryFileOrderTest {
    @Test fun missingAndInvalidValuesAreOptional() {
        listOf(null, "4", true, -1, 1.5, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE).forEach { assertNull(optionalLibraryOrder(it)) }
        assertEquals(0, optionalLibraryOrder(0))
        assertEquals(17, optionalLibraryOrder(17L))
    }
    @Test fun alphabeticalFallbackAppendsPerParentAfterExplicitSiblings() {
        val files = listOf(LibraryOrderSeed("z", null, "Zulu", null), LibraryOrderSeed("a2", null, "Alpha", null),
            LibraryOrderSeed("a1", null, "alpha", null), LibraryOrderSeed("explicit", null, "Last", 9),
            LibraryOrderSeed("nested", "folder", "Nested", null))
        val result = seedMissingLibraryOrders(files, mapOf(null to 2, "folder" to 1))
        assertEquals(mapOf("a1" to 10, "a2" to 11, "z" to 12, "nested" to 2), result)
        assertEquals(result, seedMissingLibraryOrders(files.reversed(), mapOf(null to 2, "folder" to 1)))
    }
}
