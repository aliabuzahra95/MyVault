package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.local.dao.readIntent
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit authenticated phases only, synthetic Room databases, host-owned disposable namespace. */
@RunWith(AndroidJUnit4::class)
class AuthenticatedGraphRestoreDriveTest {
    private val base get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun targetedRestoreAgainstDisposableDrive()=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("restorePhase")
        assumeTrue(phase!=null);check(phase in listOf("prepare","recover"))
        val config=JSONObject(File(base.filesDir,"disposable-drive-config.json").readText())
        check(config.getString("dbName").matches(Regex("graph-drive-disposable-[0-9a-f-]{36}\\.db")))
        val timing=BackupGraphTiming(true);val store=AuthenticatedGraphDriveTest.RemoteStore(config,timing)
        val source=BackupGraphWriterRoomTest().Fixture(nameOverride=config.getString("dbName"),remoteStore=store)
        val target=BackupGraphWriterRoomTest().Fixture(nameOverride=config.getString("dbName").replace("graph-drive-","graph-restore-drive-"),remoteStore=store)
        suspend fun restore(boundary:suspend(String)->Unit={}):GraphRestoreResult {
            val settings=BackupGraphRestoreRoomTest.FileSettings(File(target.root,"restore-settings.json"),VaultPreferences(base,target.journal).userPreferences.first().toBackupJson())
            return InternalBackupGraphRestore(target.db,target.journal,base.filesDir,File(target.root,"restored"),store,settings,boundary,timing).restore()
        }
        val resultsFile=File(base.filesDir,"graph-restore-drive-measurements.json")
        val rows=if(resultsFile.exists()&&phase=="recover")JSONArray(resultsFile.readText())else JSONArray()
        suspend fun measured(name:String):GraphRestoreResult {
            val spanStart=timing.snapshot().size;target.queries.clear();val before=target.db.backupGraphRestoreDao().applied(store.context.accountScope,store.context.lineageId)?.commitId
            val remoteTip=source.binding().commitId
            val start=store.marker("restore-$name");val began=System.nanoTime();val result=restore();val elapsed=(System.nanoTime()-began)/1000000.0;val end=store.marker("after-restore-$name")
            rows.put(JSONObject().put("name",name).put("totalMs",elapsed).put("status",result.status.name).put("commits",result.commitsApplied).put("rowsWritten",result.rowsWritten).put("binaryDownloads",result.binariesDownloaded)
                .put("appliedBefore",before?:JSONObject.NULL).put("appliedAfter",target.db.backupGraphRestoreDao().applied(store.context.accountScope,store.context.lineageId)!!.commitId).put("remoteTip",remoteTip)
                .put("requestStart",start.getInt("requests")).put("requestEnd",end.getInt("requests"))
                .put("payloadQueries",target.querySnapshot().count {it.startsWith("SELECT * FROM `")})
                .put("spans",JSONArray(timing.snapshot().drop(spanStart).map {JSONObject().put("name",it.name).put("startNanos",it.startNanos).put("endNanos",it.endNanos)})))
            resultsFile.writeText(rows.toString());return result
        }
        try {
            if(phase=="prepare") {
                store.marker("restore-root-setup");source.note();source.binary(4096);source.writer().createRoot(source.prepared())
                assertEquals(1,measured("first-full").commitsApplied)
                assertEquals(GraphRestoreStatus.ALREADY_CURRENT,measured("already-current").status)
                source.edit("Targeted note English الْعَرَبِيَّة");source.writer().publish()
                val one=measured("one-note");assertEquals(1,one.rowsWritten);assertEquals(0,one.binariesDownloaded)
                for(i in 1..3){source.edit("Three-commit continuation $i العربية");source.writer().publish()}
                val three=measured("three-commits");assertEquals(3,three.commitsApplied);assertEquals(3,three.rowsWritten)
                val old=target.db.attachmentDao().getByIdIncludingDeleted("pdf")!!
                source.binary(8192);source.writer().publish();assertEquals(1,measured("replacement").binariesDownloaded)
                assertEquals(8192,target.db.attachmentDao().getByIdIncludingDeleted("pdf")!!.sizeBytes);assertEquals(4096,File(old.localPath).length())
                source.db.attachmentDao().updateFileName("pdf","metadata-only.pdf");source.writer().publish();assertEquals(0,measured("metadata-only").binariesDownloaded)
                source.binary(1024,"new");source.writer().publish();assertEquals(1,measured("new-attachment").binariesDownloaded)
                source.db.attachmentDao().deleteByIds(listOf("new"));source.writer().publish();measured("exact-delete");assertNull(target.db.attachmentDao().getByIdIncludingDeleted("new"))
                assertTrue(target.pending().isEmpty())
                val bytes=JSONObject();val refs=store.commits()
                source.db.openHelper.readableDatabase.query("SELECT objectId FROM backup_graph_publication_objects").use {c->while(c.moveToNext()){val id=c.getString(0);bytes.put(id,android.util.Base64.encodeToString(checkNotNull(store.bytes(id)),android.util.Base64.NO_WRAP))}}
                val bundle=JSONObject().put("accountId",store.context.driveAccountId).put("lineageId",store.context.lineageId).put("objects",bytes)
                    .put("refs",JSONArray(refs.map {JSONObject().put("cloudFileId",it.objectRef.cloudFileId).put("sha256",it.objectRef.sha256).put("size",it.objectRef.size)}))
                bundle.put("expectedNotes",JSONArray(source.db.noteDao().getAllIncludingDeleted().map {it.toJson()}))
                File(base.filesDir,"authenticated-graph-fixtures").apply {mkdirs()}.resolve("targeted-linear.json").writeText(bundle.toString())
                source.binary(2048);source.writer().publish();store.marker("restore-crash-staged")
                var interrupted=false;try {restore {if(it=="BINARY_STAGED")error("Disposable stop after staging")}}catch(_:IllegalStateException){interrupted=true};assertTrue(interrupted)
                File(target.root,"before-pid").writeText(android.os.Process.myPid().toString())
            } else {
                assertNotEquals(File(target.root,"before-pid").readText().toInt(),android.os.Process.myPid())
                val pending=target.db.backupGraphRestoreDao().unfinished().single();val before=target.db.backupGraphRestoreDao().applied(store.context.accountScope,store.context.lineageId)!!
                val recovered=measured("process-recovery");assertEquals(0,recovered.binariesDownloaded)
                val after=target.db.backupGraphRestoreDao().applied(store.context.accountScope,store.context.lineageId)!!
                assertNotEquals(before.commitId,after.commitId);assertEquals(source.binding().commitId,after.commitId)
                assertEquals("COMPLETE",target.db.backupGraphRestoreDao().readIntent(store.context.accountScope,pending.operationId)!!.status)
                assertTrue(target.pending().isEmpty());assertEquals(GraphRestoreStatus.ALREADY_CURRENT,measured("after-recovery-current").status)
            }
        }finally {source.db.close();target.db.close()}
    }
}
