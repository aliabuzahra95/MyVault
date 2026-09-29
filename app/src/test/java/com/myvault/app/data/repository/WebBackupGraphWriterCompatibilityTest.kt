package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.util.Base64

class WebBackupGraphWriterCompatibilityTest {
    @Test fun chromiumIndexedDbWriterRoundTripsThroughAndroidReader() {
        val directory = System.getenv("MYVAULT_WEB_GRAPH_EVIDENCE")
        assumeNotNull(directory)
        val bundle = JSONObject(File(directory!!, "web-writer.json").readText())
        fun bytes(id: String): ByteArray = Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(id))
        val refs = bundle.getJSONArray("refs")
        val objects = (0 until refs.length()).map { index ->
            BackupGraphFixtures.ref(refs.getJSONObject(index)).let { GraphObject(it, bytes(it.cloudFileId)) }
        }
        val graph = BackupGraph.discover(objects, bundle.getString("accountId"), bundle.getString("lineageId"))
        assertEquals("${graph.issues}", GraphStatus.SINGLE_TIP, graph.status)
        val requested = mutableListOf<String>()
        val result = BackupGraphReconstruction.read(graph) { id -> requested += id; bytes(id) }
        val notes = JSONArray(result.files.getValue("notes.json"))
        val rows = (0 until notes.length()).map(notes::getJSONObject)
        assertEquals(setOf("n", "keep", "three"), rows.map { it.getString("id") }.toSet())
        val note = rows.single { it.getString("id") == "n" }
        assertEquals(bundle.getString("expectedTitle"), note.getString("title"))
        assertEquals("Original العربية", note.getString("bodyPlainText"))
        assertEquals("العربية", note.getJSONObject("unknownField").getString("keep"))
        val blocks = JSONArray(result.files.getValue("blocks.json"))
        val rich = (0 until blocks.length()).map(blocks::getJSONObject).single { it.getString("noteId") == "n" }
        val body = JSONObject(rich.getString("content"))
        assertEquals("Bold", body.getJSONArray("styleMarks").getJSONObject(0).getString("style"))
        assertEquals("keep", body.getJSONArray("noteLinks").getJSONObject(0).getString("noteId"))
        assertTrue(body.getJSONObject("futureEnvelopeField").getBoolean("keep"))
        assertEquals(8192L, result.binaries!!.single { it.attachmentId == "pdf" }.size)
        assertEquals(1024L, result.binaries.single { it.attachmentId == "new-pdf" }.size)
        assertTrue(result.permanentDeletions.any { it.file == "pdf_reading_progress.json" && it.key == listOf("pdf") })
        val checkpoint = graph.plan().checkpoint!!
        val entries = JSONObject(bytes(checkpoint.objectRef.cloudFileId).toString(Charsets.UTF_8)).getJSONArray("entries")
        val originalPdf = (0 until entries.length()).map(entries::getJSONObject).single { it.optString("backupEntry") == "files/pdf" }
        assertEquals(4096L, originalPdf.getLong("size"))
        assertFalse("Cold reconstruction must not download stale checkpoint PDF bytes", requested.contains(originalPdf.getString("cloudFileId")))
        assertEquals(GraphStatus.ALREADY_CURRENT, graph.plan(graph.tips.single()).status)
        assertThrows(IllegalStateException::class.java) {
            BackupGraphReconstruction.read(graph) { id ->
                if (id == result.binaries.single { it.attachmentId == "pdf" }.cloudFileId) ByteArray(4096) else bytes(id)
            }
        }
    }
}
