package com.myvault.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GraphDriveListingTest {
    private fun row(id: String) = GraphDriveFile(id, id, "application/json", listOf("folder"), size = 1, sha256 = "a".repeat(64))
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Partial/ambiguous listing must fail") } catch (_: IllegalStateException) { }
    }
    @Test fun allPagesIncludingEmptyContinuationPagesMustComplete() = runBlocking {
        val requested = mutableListOf<String?>()
        val rows = completeGraphDriveListing { token ->
            requested += token
            when (token) {
                null -> GraphDriveListingPage(listOf(row("a")), "second")
                "second" -> GraphDriveListingPage(emptyList(), "third")
                else -> GraphDriveListingPage(listOf(row("b")))
            }
        }
        assertEquals(listOf(null, "second", "third"), requested)
        assertEquals(listOf("a", "b"), rows.map { it.id })
    }
    @Test fun duplicateIdentitiesWithinOrAcrossPagesFail() = runBlocking {
        fails { completeGraphDriveListing { GraphDriveListingPage(listOf(row("a"), row("a"))) } }
        fails { completeGraphDriveListing { token -> GraphDriveListingPage(listOf(row("a")), if (token == null) "second" else null) } }
    }
    @Test fun incompleteSearchAndSecondPageFailureNeverReturnPartialInventory() = runBlocking {
        fails { completeGraphDriveListing { GraphDriveListingPage(listOf(row("a")), incompleteSearch = true) } }
        var visited = 0
        fails { completeGraphDriveListing { token ->
            visited++
            if (token != null) error("Disposable page failure")
            GraphDriveListingPage(listOf(row("a")), "second")
        } }
        assertEquals(2, visited)
    }
    @Test fun repeatingAndBlankPageTokensFailInsteadOfLoopingOrTruncating() = runBlocking {
        var calls = 0
        fails { completeGraphDriveListing { calls++; GraphDriveListingPage(emptyList(), "same") } }
        assertEquals(2, calls)
        fails { completeGraphDriveListing { GraphDriveListingPage(emptyList(), " ") } }
    }
}
