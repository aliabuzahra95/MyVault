package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Independent Kotlin generator for the shared, exact-byte disposable fixture format. */
internal object BackupGraphFixtures {
    const val account = "account-العربية-\"\\/😀"
    const val lineage = "lineage-العربية"
    fun uuid(n: Int) = "00000000-0000-4000-8000-${n.toString(16).padStart(12, '0')}"
    fun refJson(ref: GraphObjectRef) = JSONObject().put("cloudFileId", ref.cloudFileId).put("sha256", ref.sha256).put("size", ref.size)
    fun ref(json: JSONObject) = GraphObjectRef(json.getString("cloudFileId"), json.getString("sha256"), json.getLong("size"))

    fun generate(origin: String): JSONObject {
        val objects = JSONObject(); val cases = JSONArray()
        fun put(name: String, raw: ByteArray): GraphObjectRef {
            val id = "$origin-$name"; objects.put(id, Base64.getEncoder().encodeToString(raw))
            return GraphObjectRef(id, IncrementalBackupFormat.sha256(raw), raw.size.toLong())
        }
        fun bytes(value: Any) = value.toString().toByteArray(Charsets.UTF_8)
        val old = put("pdf-4096", ByteArray(4096) { 79 })
        val replacement = put("pdf-8192", ByteArray(8192) { 82 })
        val clip = put("clip-512", ByteArray(512) { 67 })
        fun row(size: Long, id: String = "pdf") = JSONObject().put("id", id).put("sizeBytes", size).put("fileEntry", "files/$id").put("displayName", "English العربية")
        val entries = JSONArray().put(refJson(old).put("kind", "file").put("backupEntry", "files/pdf"))
            .put(refJson(clip).put("kind", "file").put("backupEntry", "files/clip"))
            .put(refJson(put("notes", bytes(JSONArray().put(JSONObject().put("id", "n").put("bodyPlainText", "Original العربية")
                .put("styleMarks", JSONArray().put(JSONObject().put("start", 0).put("end", 8).put("bold", true))))
                .put(JSONObject().put("id", "keep").put("bodyPlainText", "Never deleted"))
                .put(JSONObject().put("id", "delete-note").put("bodyPlainText", "Explicit ID only")))))
                .put("kind", "metadata").put("fileName", "notes.json"))
            .put(refJson(put("attachments", bytes(JSONArray().put(row(4096)).put(row(512, "clip")))))
                .put("kind", "metadata").put("fileName", "attachments.json"))
        entries.put(refJson(put("metadata-manifest", bytes(JSONObject().put("version", 1)))).put("kind", "metadata").put("fileName", "manifest.json"))
        val cpRef = put("checkpoint", bytes(JSONObject().put("schemaVersion", 1).put("storage", "google-drive-api").put("cloudVersion", 100).put("entries", entries)))
        val checkpoint = GraphCheckpoint("full-${cpRef.sha256}", cpRef)
        val root = BackupGraphCommit(account, lineage, uuid(1), "checkpoint", listOf(BackupGraphCapability), emptyList(), checkpoint, null)
        fun save(name: String, c: BackupGraphCommit) = put(name, BackupGraphProtocol.encode(c))
        val r = save("R", root)
        fun upsert(file: String, value: JSONObject) = BackupRecordChange(file, listOf(value.getString("id")), value)
        val body = "Al-Kulliyat: الكليات • concepts. \"عربية\" / English"
        fun make(name: String, n: Int, parent: BackupGraphCommit, parentRef: GraphObjectRef, changes: List<BackupRecordChange>, binary: Boolean = false): Pair<BackupGraphCommit, GraphObjectRef> {
            val delta = IncrementalBackupFormat.createDelta(checkpoint.checkpointId, parent.deltaHead, "$origin-delta-$name", changes,
                if (binary) listOf(BackupBinaryDescriptor("pdf", replacement.cloudFileId, replacement.sha256, replacement.size)) else null)
            val deltaRef = put("delta-$name", bytes(delta))
            val c = root.copy(commitId = uuid(n), kind = "delta", parents = listOf(GraphParent(parent.commitId, parentRef)),
                requiredReaders = (parent.requiredReaders + "checkpoint-delta-v1" + if (binary) listOf(BackupBinaryReaderCapability) else emptyList()).distinct().sorted(),
                delta = GraphDelta(delta.getString("deltaId"), delta.getString("parentId"), deltaRef))
            return c to save(name, c)
        }
        val a = make("A", 2, root, r, listOf(upsert("notes.json", JSONObject().put("id", "n").put("bodyPlainText", body)
            .put("styleMarks", JSONArray().put(JSONObject().put("start", 0).put("end", 8).put("bold", true))))))
        val b = make("B", 3, a.first, a.second, listOf(upsert("attachments.json", row(8192))), true)
        val c = make("C", 4, b.first, b.second, listOf(upsert("attachments.json", row(8192).put("displayName", "Metadata only العربية")),
            BackupRecordChange("notes.json", listOf("delete-note")), BackupRecordChange("attachments.json", listOf("clip"))))
        fun expected(status: GraphStatus, tips: List<Int>, ordered: List<Int>? = null, applied: Int? = null): JSONObject = JSONObject()
            .put("status", status.name).put("roots", JSONArray(listOf(root.commitId))).put("tips", JSONArray(tips.map(::uuid).sorted()))
            .also { json -> if (ordered != null) {
                json.put("ordered", JSONArray(ordered.map(::uuid)))
                json.put("descendants", JSONArray((if (applied == null) ordered else ordered.drop(ordered.indexOf(applied) + 1)).map(::uuid)))
            } }
        fun restore(text: String, size: Long, notes: List<String>, clipPresent: Boolean) = JSONObject().put("body", text).put("pdfSize", size)
            .put("notes", JSONArray(notes)).put("clipPresent", clipPresent)
        fun add(name: String, refs: List<GraphObjectRef>, expectation: JSONObject, applied: String? = null, restore: JSONObject? = null) {
            cases.put(JSONObject().put("name", name).put("refs", JSONArray(refs.map(::refJson))).put("applied", applied ?: JSONObject.NULL)
                .put("expected", expectation).also { if (restore != null) it.put("restore", restore) })
        }
        fun status(value: GraphStatus) = JSONObject().put("status", value.name)
        val fullNotes = listOf("delete-note", "keep", "n")
        val chain = listOf(r, a.second, b.second, c.second)
        add("historical", emptyList(), status(GraphStatus.HISTORICAL))
        add("root", listOf(r), expected(GraphStatus.SINGLE_TIP, listOf(1), listOf(1)), restore = restore("Original العربية", 4096, fullNotes, true))
        add("linear", chain.reversed(), expected(GraphStatus.SINGLE_TIP, listOf(4), listOf(1, 2, 3, 4)))
        add("local-A", chain, expected(GraphStatus.DESCENDANTS, listOf(4), listOf(1, 2, 3, 4), 2).put("deltas", JSONArray(listOf(b.first.delta!!.deltaId, c.first.delta!!.deltaId))), a.first.commitId)
        add("current", chain, expected(GraphStatus.ALREADY_CURRENT, listOf(4), listOf(1, 2, 3, 4), 4).put("deltas", JSONArray()), c.first.commitId)
        val sibling = make("sibling", 5, root, r, listOf(upsert("notes.json", JSONObject().put("id", "n").put("bodyPlainText", "Other branch"))))
        add("fork", listOf(r, a.second, sibling.second), expected(GraphStatus.FORK, listOf(2, 5)))
        val other = make("other", 6, a.first, a.second, listOf(upsert("notes.json", JSONObject().put("id", "n").put("bodyPlainText", "Deeper branch"))))
        val deep = make("deep", 7, other.first, other.second, listOf(upsert("notes.json", JSONObject().put("id", "n").put("bodyPlainText", "Other tip"))))
        add("deeper-fork", listOf(r, a.second, b.second, other.second, deep.second), expected(GraphStatus.FORK, listOf(3, 7)))
        add("missing-parent", listOf(r, b.second), status(GraphStatus.MISSING_ANCESTRY))
        val fake = GraphObjectRef("$origin-cycle", "a".repeat(64), 100)
        val x = a.first.copy(commitId = uuid(8), parents = listOf(GraphParent(uuid(9), fake)))
        val y = a.first.copy(commitId = uuid(9), parents = listOf(GraphParent(uuid(8), fake)))
        add("cycle", listOf(r, save("cycle-X", x), save("cycle-Y", y)), status(GraphStatus.CORRUPT))
        add("wrong-checkpoint", listOf(r, save("wrong-checkpoint", a.first.copy(checkpoint = checkpoint.copy(objectRef = cpRef.copy(cloudFileId = "other-checkpoint-object"))))), status(GraphStatus.CORRUPT))
        val corrupted = put("corrupt-A", byteArrayOf(1, 2, 3))
        add("corrupt-bytes", listOf(r, corrupted.copy(sha256 = a.second.sha256, size = a.second.size)), status(GraphStatus.CORRUPT))
        val unsupported = JSONObject(BackupGraphProtocol.utf8(BackupGraphProtocol.encode(a.first))).put("requiredReaders", JSONArray((a.first.requiredReaders + "future-reader").sorted()))
        add("unsupported", listOf(r, put("unsupported", bytes(unsupported))), status(GraphStatus.UNSUPPORTED))
        add("metadata", listOf(r, a.second), expected(GraphStatus.SINGLE_TIP, listOf(2), listOf(1, 2)), restore = restore(body, 4096, fullNotes, true))
        add("binary-replacement", listOf(r, a.second, b.second), expected(GraphStatus.SINGLE_TIP, listOf(3), listOf(1, 2, 3)), restore = restore(body, 8192, fullNotes, true))
        add("explicit-delete", chain, expected(GraphStatus.SINGLE_TIP, listOf(4), listOf(1, 2, 3, 4)), restore = restore(body, 8192, listOf("keep", "n"), false))
        add("divergent-local", listOf(r, a.second), status(GraphStatus.DIVERGENT), uuid(90))
        add("duplicate-intent", listOf(r, r, a.second, a.second), expected(GraphStatus.SINGLE_TIP, listOf(2), listOf(1, 2)))
        add("conflicting-UUID", listOf(r, a.second, save("conflicting-A", a.first.copy(delta = a.first.delta!!.copy(objectRef = a.first.delta!!.objectRef.copy(cloudFileId = "different-delta"))))), status(GraphStatus.CORRUPT))
        add("foreign-account", listOf(r, save("foreign", a.first.copy(accountId = "other-account"))), status(GraphStatus.CORRUPT))
        add("foreign-lineage", listOf(r, save("foreign-lineage", a.first.copy(lineageId = "other-lineage"))), status(GraphStatus.CORRUPT))
        add("multiple-roots", listOf(r, save("other-root", root.copy(commitId = uuid(99)))), status(GraphStatus.FORK))
        add("capability-downgrade", chain.dropLast(1) + save("downgrade", c.first.copy(requiredReaders = a.first.requiredReaders)), status(GraphStatus.CORRUPT))
        add("parent-hash-mismatch", listOf(r, save("wrong-parent", a.first.copy(parents = listOf(GraphParent(root.commitId, r.copy(sha256 = "b".repeat(64))))))), status(GraphStatus.CORRUPT))
        val rootText = BackupGraphProtocol.utf8(BackupGraphProtocol.encode(root))
        listOf(
            Triple("duplicate-key", rootText.replace("\"version\":1", "\"version\":1,\"version\":1"), GraphStatus.CORRUPT),
            Triple("decimal-version", rootText.replace("\"version\":1", "\"version\":1.0"), GraphStatus.CORRUPT),
            Triple("string-version", rootText.replace("\"version\":1", "\"version\":\"1\""), GraphStatus.CORRUPT),
            Triple("future-version", rootText.replace("\"version\":1", "\"version\":2"), GraphStatus.UNSUPPORTED),
            Triple("reordered-fields", rootText.replace("{\"format\":\"myvault-backup-commit\",\"version\":1", "{\"version\":1,\"format\":\"myvault-backup-commit\""), GraphStatus.CORRUPT),
        ).forEach { (name, text, expected) -> add(name, listOf(put(name, text.toByteArray(Charsets.UTF_8))), status(expected)) }
        val vector = root.copy(accountId = "عربية-\"\\/😀", lineageId = "العلم-\u2028-\u2029")
        val vectorRef = save("unicode-vector", vector)
        return JSONObject().put("origin", origin).put("accountId", account).put("lineageId", lineage).put("objects", objects).put("cases", cases)
            .put("vector", JSONObject().put("ref", refJson(vectorRef)).put("intent", JSONObject(BackupGraphProtocol.utf8(BackupGraphProtocol.encode(vector)))))
    }
}
