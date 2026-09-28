package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.*
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence codec only; remote commit/delta bytes use the existing protocol codecs. */
internal object BackupGraphIntentCodec {
    fun account(a: BackupTrackingAccount) = JSONObject().put("accountScope", a.accountScope).put("trusted", a.trusted)
        .put("checkpointId", a.checkpointId ?: JSONObject.NULL).put("headId", a.headId ?: JSONObject.NULL)
        .put("manifestId", a.manifestId ?: JSONObject.NULL).put("manifestSha256", a.manifestSha256 ?: JSONObject.NULL).put("reason", a.reason).toString()
    fun account(s: String): BackupTrackingAccount = JSONObject(s).let {
        BackupTrackingAccount(it.getString("accountScope"), it.getBoolean("trusted"), nullable(it, "checkpointId"), nullable(it, "headId"),
            nullable(it, "manifestId"), nullable(it, "manifestSha256"), it.getString("reason"))
    }
    fun binding(a: BackupGraphBinding): String = JSONObject().put("accountScope", a.accountScope).put("lineageId", a.lineageId)
        .put("driveAccountId", a.driveAccountId).put("commitId", a.commitId).put("commitFileId", a.commitFileId)
        .put("commitSha256", a.commitSha256).put("commitSize", a.commitSize).put("checkpointId", a.checkpointId)
        .put("checkpointFileId", a.checkpointFileId).put("checkpointSha256", a.checkpointSha256).put("checkpointSize", a.checkpointSize)
        .put("deltaHeadId", a.deltaHeadId).put("originEpoch", a.originEpoch).toString()
    fun binding(s: String): BackupGraphBinding = JSONObject(s).let {
        BackupGraphBinding(it.getString("accountScope"), it.getString("lineageId"), it.getString("driveAccountId"), it.getString("commitId"),
            it.getString("commitFileId"), it.getString("commitSha256"), it.getLong("commitSize"), it.getString("checkpointId"),
            it.getString("checkpointFileId"), it.getString("checkpointSha256"), it.getLong("checkpointSize"), it.getString("deltaHeadId"), it.getLong("originEpoch"))
    }
    fun batch(a: PendingBackupBatch) = JSONObject().put("records", JSONArray(a.records.map {
        JSONObject().put("group", it.group).put("key", JSONArray(it.key)).put("operation", it.operation).put("generation", it.generation)
            .put("payload", it.payloadJson ?: JSONObject.NULL).put("dependencies", JSONArray(it.dependencies.map { d ->
                JSONObject().put("group", d.group).put("key", JSONArray(d.key)) }))
    })).put("binaries", JSONArray(a.binaries.map {
        JSONObject().put("attachmentId", it.attachmentId).put("path", it.localPath).put("size", it.byteSize)
            .put("sha256", it.sha256 ?: JSONObject.NULL).put("status", it.status).put("reuse", it.reusableCloudFileId ?: JSONObject.NULL)
    })).put("queries", JSONArray(a.queriedRecords.map { JSONObject().put("group", it.group).put("key", JSONArray(it.key)) }))
        .put("settingsReads", a.settingsReadCount).toString()
    fun records(s: String): List<CapturedBackupRecord> = array(JSONObject(s).getJSONArray("records")).map {
        CapturedBackupRecord(it.getString("group"), strings(it.getJSONArray("key")), it.getString("operation"), it.getLong("generation"),
            nullable(it, "payload"), array(it.getJSONArray("dependencies")).map { d -> BackupRecordIdentity(d.getString("group"), strings(d.getJSONArray("key"))) })
    }
    fun binaries(s: String): List<CapturedBackupBinary> = array(JSONObject(s).getJSONArray("binaries")).map {
        CapturedBackupBinary(it.getString("attachmentId"), it.getString("path"), it.getLong("size"), nullable(it, "sha256"), it.getString("status"), nullable(it, "reuse"))
    }
    fun snapshot(p: BackupGraphPublication): CapturedBackupChanges = CapturedBackupChanges(account(p.originalAccountJson), p.capturedGeneration,
        p.capturedOriginEpoch, records(p.frozenBatchJson).map { r -> BackupPendingChange(p.accountScope, r.group, r.key[0], r.key.getOrElse(1) { "" },
            r.key.getOrElse(2) { "" }, r.operation, r.generation) })
    private fun nullable(j: JSONObject, key: String) = if (j.isNull(key)) null else j.getString(key)
    fun array(a: JSONArray): List<JSONObject> = (0 until a.length()).map(a::getJSONObject)
    private fun strings(a: JSONArray) = (0 until a.length()).map(a::getString)
}
