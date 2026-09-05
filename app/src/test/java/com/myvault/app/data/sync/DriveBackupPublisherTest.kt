package com.myvault.app.data.sync

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class DriveBackupPublisherTest {
    private fun ByteArray.hash() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private inner class Fixture(private val failure: String? = null) {
        val directory = Files.createTempDirectory("drive-publication-test").toFile()
        val files = linkedMapOf<String, ByteArray>()
        val original = linkedMapOf<String, ByteArray>()
        var committed: PublishedDriveBackup
        var publicationCount = 0
        var created = 0
        var lastSuccess = 1L
        var archived: PublishedDriveBackup? = null
        var committedReads = 0
        val inputs: List<DriveBackupUpload>

        init {
            val records = linkedMapOf(
                "notes.json" to "[{\"id\":\"study-note\",\"folderId\":\"study\",\"title\":\"A\"},{\"id\":\"course-note\",\"folderId\":\"course-root\",\"title\":\"A\"}]",
                "courses.json" to "[{\"id\":\"course\",\"rootFolderId\":\"course-root\"}]",
                "blocks.json" to "[{\"id\":\"block\",\"noteId\":\"course-note\",\"type\":\"rich_text\",\"content\":\"A\\n\\nparagraph\"}]",
                "attachments.json" to "[{\"id\":\"pdf\",\"fileEntry\":\"files/pdf\"}]",
                "pdf_annotations.json" to "[{\"id\":\"highlight\",\"attachmentId\":\"pdf\",\"pageIndex\":0,\"left\":1,\"right\":2}]",
                "pdf_reading_progress.json" to "[{\"attachmentId\":\"pdf\",\"pageIndex\":0}]",
                "pdf.bin" to "%PDF-test-A",
            )
            val oldEntries = JSONArray()
            inputs = records.map { (name, body) ->
                val kind = if (name.endsWith(".json")) "metadata" else "file"
                val path = if (kind == "file") "files/$name" else "metadata/$name"
                val bytes = body.toByteArray()
                val id = "old-$name"
                files[id] = bytes
                original[id] = bytes.copyOf()
                oldEntries.put(JSONObject().put("path", path).put("fileName", name).put("kind", kind)
                    .put("cloudFileId", id).put("size", bytes.size).put("sha256", bytes.hash()))
                val changed = body.replace("A", "B").toByteArray()
                val file = File(directory, name).apply { writeBytes(changed) }
                DriveBackupUpload(path, name, kind, file, changed.size.toLong(), changed.hash())
            }
            committed = PublishedDriveBackup("manifest", JSONObject().put("schemaVersion", 1).put("cloudVersion", 1).put("entries", oldEntries).toString())
        }

        val transport = object : DriveBackupPublicationTransport {
            override fun readCommitted(): PublishedDriveBackup {
                committedReads += 1
                if (failure == "before-publish" && committedReads == 2) error("interrupt before publication")
                if (failure == "after-publish" && publicationCount > 0) error("lost readback")
                if (failure == "concurrent" && committedReads == 2) return committed.copy(text = committed.text + " ")
                return committed
            }
            override fun existingMatches(id: String, kind: String, size: Long, sha256: String) = verify(id, size, sha256)
            override fun create(upload: DriveBackupUpload): String {
                val id = "new-${++created}"
                files[id] = upload.file.readBytes()
                if (failure == "first-metadata" && created == 1) error("interrupt first metadata")
                if (failure == "middle-metadata" && created == 2) error("interrupt middle metadata")
                if (failure == "binary" && upload.kind == "file") files[id] = byteArrayOf(1)
                return id
            }
            override fun verify(id: String, size: Long, sha256: String) =
                files[id]?.let { uploadedBytesMatchManifest(it, size, sha256) } ?: false
            override fun preserve(previous: PublishedDriveBackup) { archived = previous }
            override fun publish(previousId: String?, text: String) {
                publicationCount += 1
                assertEquals("manifest", previousId)
                if (failure == "publish-rejected") error("publication not accepted")
                committed = PublishedDriveBackup("manifest", text)
                if (failure == "publish-response-lost") error("server accepted; response lost")
            }
        }

        suspend fun attempt(): DriveBackupPublicationResult {
            val result = DriveBackupPublisher(transport).publish(
                previous = committed,
                uploads = inputs,
                manifest = { ids ->
                    val entries = JSONArray()
                    inputs.forEach { upload -> entries.put(JSONObject().put("path", upload.path).put("fileName", upload.name)
                        .put("kind", upload.kind).put("cloudFileId", ids.getValue(upload.path))
                        .put("size", upload.size).put("sha256", upload.sha256)) }
                    JSONObject().put("schemaVersion", 1).put("cloudVersion", 2).put("entries", entries).toString()
                },
                checkAccount = {},
            )
            lastSuccess = 2
            return result
        }

        fun assertReadable(snapshot: PublishedDriveBackup) {
            val entries = JSONObject(snapshot.text).getJSONArray("entries")
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val bytes = files.getValue(entry.getString("cloudFileId"))
                assertTrue(uploadedBytesMatchManifest(bytes, entry.getLong("size"), entry.getString("sha256")))
                if (entry.getString("kind") == "metadata") JSONArray(bytes.toString(Charsets.UTF_8))
            }
        }
        fun assertOriginalBytes() = original.forEach { (id, bytes) -> assertArrayEquals(bytes, files[id]) }
        fun close() { directory.deleteRecursively() }
    }

    @Test fun interruptionsKeepPreviousManifestAndEveryReferencedByte() = runBlocking {
        for (failure in listOf("first-metadata", "middle-metadata", "binary", "before-publish", "publish-rejected", "concurrent")) {
            val fixture = Fixture(failure)
            try {
                val previous = fixture.committed
                assertTrue(failure, runCatching { fixture.attempt() }.isFailure)
                assertEquals(previous, fixture.committed)
                fixture.assertOriginalBytes()
                fixture.assertReadable(previous)
                assertEquals(previous, fixture.archived)
                assertEquals(1L, fixture.lastSuccess)
            } finally { fixture.close() }
        }
    }

    @Test fun lostPublicationResponseIsReadBackWithoutPublishingTwice() = runBlocking {
        val fixture = Fixture("publish-response-lost")
        try {
            fixture.attempt()
            assertEquals(1, fixture.publicationCount)
            assertEquals(2L, fixture.lastSuccess)
            fixture.assertReadable(fixture.committed)
            fixture.assertReadable(fixture.archived!!)
            fixture.assertOriginalBytes()
        } finally { fixture.close() }
    }

    @Test fun failureAfterPublicationRetainsValidCandidateAndPriorGeneration() = runBlocking {
        val fixture = Fixture("after-publish")
        try {
            assertTrue(runCatching { fixture.attempt() }.isFailure)
            assertEquals(1L, fixture.lastSuccess)
            assertEquals(2, JSONObject(fixture.committed.text).getInt("cloudVersion"))
            fixture.assertReadable(fixture.committed)
            fixture.assertReadable(fixture.archived!!)
            fixture.assertOriginalBytes()
        } finally { fixture.close() }
    }

    @Test fun successUsesExactIdsDespiteDuplicateNamesAndReusesUnchangedObjects() = runBlocking {
        val fixture = Fixture()
        try {
            val result = fixture.attempt()
            assertEquals(2, result.uploadedMetadata)
            assertEquals(1, result.uploadedFiles)
            fixture.assertReadable(fixture.committed)
            fixture.assertReadable(fixture.archived!!)
            fixture.assertOriginalBytes()
            assertTrue(fixture.files.values.any { it.toString(Charsets.UTF_8).contains("\"title\":\"B\"") })
            assertTrue(result.retainedBytes > 0)
        } finally { fixture.close() }
    }

    @Test fun ambiguousLegacyIdsStopBeforeAnyDuplicateNameUpload() = runBlocking {
        val fixture = Fixture()
        try {
            val json = JSONObject(fixture.committed.text)
            json.getJSONArray("entries").getJSONObject(0).remove("cloudFileId")
            fixture.committed = fixture.committed.copy(text = json.toString())
            assertTrue(runCatching { fixture.attempt() }.isFailure)
            assertEquals(0, fixture.created)
            assertNull(fixture.archived)
            assertEquals(0, fixture.publicationCount)
        } finally { fixture.close() }
    }
}
