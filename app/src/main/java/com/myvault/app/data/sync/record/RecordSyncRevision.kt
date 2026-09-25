package com.myvault.app.data.sync.record

import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal const val RecordSyncDebounceMs = 4_000L

internal data class RecordSyncRevision(
    val entityType: String,
    val entityId: String,
    val revisionId: String,
    val mutationId: String,
    val clientId: String,
    val parents: List<String>,
    val deleted: Boolean,
    val payloadJson: String?,
    val contentHash: String,
    val dependencies: List<String>,
    val publishedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("entityType", entityType)
        .put("entityId", entityId)
        .put("revisionId", revisionId)
        .put("mutationId", mutationId)
        .put("clientId", clientId)
        .put("parents", JSONArray(parents))
        .put("deleted", deleted)
        .put("payloadJson", payloadJson ?: JSONObject.NULL)
        .put("contentHash", contentHash)
        .put("dependencies", JSONArray(dependencies))
        .put("publishedAt", publishedAt)

    companion object {
        fun create(type: String, id: String, clientId: String, parents: List<String>, payloadJson: String?, dependencies: List<String> = emptyList()): RecordSyncRevision {
            require(type == "note" || type == "folder")
            require(id.isNotBlank() && clientId.isNotBlank())
            if (payloadJson != null) require(JSONObject(payloadJson).getString("id") == id)
            val canonicalPayload = payloadJson?.let { canonicalJson(JSONObject(it)) }
            val revisionId = UUID.randomUUID().toString()
            return RecordSyncRevision(type, id, revisionId, revisionId, clientId, parents.distinct(), canonicalPayload == null, canonicalPayload, sha256(canonicalPayload ?: "null"), dependencies.distinct(), System.currentTimeMillis())
        }

        fun parse(json: JSONObject): RecordSyncRevision {
            require(json.getInt("schemaVersion") == 1)
            val type = json.getString("entityType")
            require(type == "note" || type == "folder")
            val payload = if (json.isNull("payloadJson")) null else json.getString("payloadJson")
            val revision = RecordSyncRevision(
                type, json.getString("entityId"), json.getString("revisionId"), json.getString("mutationId"),
                json.getString("clientId"), json.getJSONArray("parents").strings(), json.getBoolean("deleted"),
                payload, json.getString("contentHash"), json.getJSONArray("dependencies").strings(), json.getLong("publishedAt"),
            )
            require(revision.entityId.isNotBlank() && revision.revisionId.isNotBlank() && revision.mutationId.isNotBlank())
            require(revision.deleted == (payload == null))
            if (payload != null) require(JSONObject(payload).getString("id") == revision.entityId)
            require(revision.contentHash == sha256(payload ?: "null"))
            return revision
        }
    }
}

internal fun revisionDecision(localHead: String?, incoming: RecordSyncRevision): String = when {
    localHead == incoming.revisionId -> "already-applied"
    localHead == null && incoming.parents.isEmpty() -> "apply"
    localHead != null && localHead in incoming.parents -> "apply"
    else -> "conflict"
}

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

internal fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

internal fun canonicalJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key ->
        "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}"
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { index -> canonicalJson(value.get(index)) }
    is String -> JSONObject.quote(value)
    is Boolean, is Number -> value.toString()
    else -> error("Unsupported sync JSON value.")
}
