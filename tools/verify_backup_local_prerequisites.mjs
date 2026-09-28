import assert from "node:assert/strict";
import { readFileSync, mkdtempSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { DatabaseSync } from "node:sqlite";

const directory = process.env.MYVAULT_JOURNAL_TEST_DIR;
assert.ok(directory, "Set MYVAULT_JOURNAL_TEST_DIR to the Kotlin SQL-fixture directory.");
const base = JSON.parse(readFileSync(join(directory, "journal-sql.json"), "utf8"));
const fixture = JSON.parse(readFileSync(join(directory, "local-prerequisites.json"), "utf8"));
const disposable = mkdtempSync(join(directory, "local-prerequisites-disposable-"));
const path = join(disposable, "test.sqlite");
let db = new DatabaseSync(path);
const pending = (scope = "a@example.com") => db.prepare("SELECT * FROM backup_pending_changes WHERE accountScope=? ORDER BY recordGroup,key0,key1,key2").all(scope);
const clock = () => db.prepare(base.queries.clock).get();
let hashReads = 0;
const hash = (bytes) => { hashReads++; return createHash("sha256").update(bytes).digest("hex"); };
const fingerprint = (id) => db.prepare("SELECT * FROM backup_binary_fingerprints WHERE attachmentId=?").get(id);
const reference = (scope, id) => db.prepare("SELECT * FROM backup_binary_references WHERE accountScope=? AND attachmentId=?").get(scope, id);
const note = (id, title) => db.prepare("INSERT INTO notes VALUES (?,NULL,NULL,?,'English العربية',0,0,0,0,10,11,NULL)").run(id, title);

function capture(scope = "a@example.com") {
  const reads = [];
  const rows = [];
  const cache = new Map();
  const read = (group, keys) => {
    const token = JSON.stringify([group, keys]);
    if (!cache.has(token)) {
      reads.push({ group, keys, sql: fixture.queries[group] });
      cache.set(token, db.prepare(fixture.queries[group]).get(...keys));
    }
    return cache.get(token);
  };
  db.exec("BEGIN");
  try {
    const account = db.prepare("SELECT * FROM backup_tracking_accounts WHERE accountScope=?").get(scope);
    const generation = clock().generation;
    const work = pending(scope);
    for (const item of work) {
      const keys = [item.key0, item.key1, item.key2].slice(0, base.groups[item.recordGroup]?.length ?? 1);
      const payload = item.operation === "DELETE" ? null : read(item.recordGroup, keys);
      assert.ok(item.operation === "DELETE" || payload, "Absence cannot become deletion");
      if (item.recordGroup === "attachments.json" && payload) fingerprint(payload.id);
      if (item.recordGroup === "pdf_annotations.json" && payload) { read("attachments.json", [payload.attachmentId]); fingerprint(payload.attachmentId); }
      rows.push({ group: item.recordGroup, keys, operation: item.operation, payload: payload ? { ...payload } : null });
    }
    db.exec("COMMIT");
    return { account, generation, rows, reads };
  } catch (error) { db.exec("ROLLBACK"); throw error; }
}

try {
  db.exec("PRAGMA foreign_keys=ON");
  for (const entity of fixture.schema33.database.entities) {
    db.exec(entity.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
    for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
  }
  for (const entity of fixture.schema33.database.entities) for (const trigger of entity.contentSyncTriggers ?? []) db.exec(trigger);
  note("existing", "Unmodified Arabic العربية");
  db.prepare("INSERT INTO blocks VALUES ('rich','existing','rich_text',?,0)").run(JSON.stringify({text:"العربية English",styleMarks:[{start:0,end:7,style:"bold"}],noteLinks:[]}));
  db.prepare("INSERT INTO attachments VALUES ('old-pdf','existing',NULL,'old.pdf','application/pdf',3,?,NULL,0,10,NULL,NULL)").run(join(disposable,"old.pdf"));
  const beforeNote = db.prepare("SELECT * FROM notes").all(); const beforeBlock = db.prepare("SELECT * FROM blocks").all();
  db.exec("PRAGMA user_version=33; BEGIN");
  fixture.migration.forEach((sql) => db.exec(sql));
  db.exec("PRAGMA user_version=34; COMMIT");
  assert.deepEqual(db.prepare("SELECT * FROM notes").all(), beforeNote);
  assert.deepEqual(db.prepare("SELECT * FROM blocks").all(), beforeBlock);
  assert.equal(fingerprint("old-pdf"), undefined, "Pre-existing hashes must not be fabricated");
  // Install the actual Kotlin-generated schema/capture triggers, without scanning old user rows.
  base.migration.filter((sql) => sql.startsWith("INSERT")).forEach((sql) => db.exec(sql));
  Object.values(base.triggers).flat().forEach((sql) => db.exec(sql));
  fixture.binaryTriggers.forEach((sql) => db.exec(sql));
  for (const scope of ["a@example.com", "b@example.com"]) db.prepare("INSERT INTO backup_tracking_accounts VALUES (?,0,NULL,NULL,NULL,NULL,'baseline_required')").run(scope);
  const initialHashes = hashReads;
  const empty = capture(); assert.equal(empty.rows.length, 0); assert.equal(empty.reads.length, 0); assert.equal(hashReads, initialHashes);
  assert.equal(empty.account.trusted, 0);
  note("one", "First");
  const single = capture(); assert.equal(single.rows.length, 1); assert.deepEqual(single.reads.map((read) => read.group), ["notes.json"]); assert.equal(hashReads, initialHashes);
  note("two", "Second");
  db.prepare("INSERT INTO blocks VALUES ('new-rich','one','rich_text',?,0)").run(JSON.stringify({text:"English العربية",styleMarks:[],noteLinks:[]}));
  const three = capture(); assert.equal(three.rows.length, 3); assert.equal(three.reads.length, 3);
  db.exec("UPDATE notes SET title='Changed after capture' WHERE id='one'");
  assert.equal(three.rows.find((row) => row.keys[0] === "one").payload.title, "First");
  db.prepare(base.queries.ack).run({scope:"a@example.com",generation:three.generation});
  assert.equal(pending().length, 1); assert.equal(pending()[0].key0, "one");
  assert.equal(pending("b@example.com").length, 3);
  db.exec("DELETE FROM notes WHERE id='two'");
  const deletion = capture(); assert.equal(deletion.rows.find((row) => row.keys[0] === "two").payload, null);
  assert.ok(!deletion.reads.some((read) => read.keys[0] === "two"));
  db.prepare(base.queries.ack).run({scope:"a@example.com",generation:clock().generation});

  // Real equal-size byte replacement, same logical ID, new SHA; local bookkeeping commits atomically.
  const binaryPath = join(disposable, "owned.pdf");
  function writeBinary(bytes) {
    writeFileSync(binaryPath, bytes);
    const digest = hash(bytes);
    db.exec("BEGIN");
    db.prepare("INSERT OR REPLACE INTO attachments VALUES ('pdf','existing',NULL,'file.pdf','application/pdf',?,?,NULL,0,10,NULL,NULL)").run(bytes.length, binaryPath);
    db.prepare(base.queries.tick).run();
    db.prepare("INSERT OR REPLACE INTO backup_pending_changes SELECT accountScope,'attachments.json','pdf','','','UPSERT',generation FROM backup_tracking_accounts,backup_journal_state WHERE backup_journal_state.id=1").run();
    db.prepare("INSERT OR REPLACE INTO backup_binary_fingerprints VALUES ('pdf',?,?,?,'VERIFIED',?)").run(binaryPath,bytes.length,digest,clock().generation);
    db.exec("COMMIT");
    return digest;
  }
  const oldHash = writeBinary(Buffer.from("old")); const newHash = writeBinary(Buffer.from("new"));
  assert.notEqual(oldHash,newHash); assert.equal(fingerprint("pdf").sha256,newHash);
  assert.equal(pending().filter((row) => row.key0 === "pdf").length,1);
  const hashesBeforeCapture = hashReads; capture(); capture(); assert.equal(hashReads,hashesBeforeCapture);
  db.exec("UPDATE attachments SET deletedAt=22 WHERE id='pdf'"); assert.equal(fingerprint("pdf").status,"VERIFIED");
  db.exec("UPDATE attachments SET localPath='/changed/path' WHERE id='pdf'"); assert.equal(fingerprint("pdf").status,"UNKNOWN"); assert.equal(fingerprint("pdf").sha256,null);
  db.exec("DELETE FROM attachments WHERE id='pdf'"); assert.equal(fingerprint("pdf").status,"DELETED"); assert.equal(pending().find((row) => row.key0 === "pdf").operation,"DELETE");

  // Use the byte-verified proof emitted by Kotlin; later generation and other accounts survive its ack.
  const proof = fixture.proof;
  const baselineGeneration = clock().generation;
  note("later", "Newer than baseline capture");
  db.exec("BEGIN");
  db.prepare(base.queries.ack).run({scope:proof.account,generation:baselineGeneration});
  db.prepare("UPDATE backup_tracking_accounts SET trusted=1,checkpointId=?,headId=?,manifestId=?,manifestSha256=?,reason='verified_commit' WHERE accountScope=?").run(proof.checkpoint,proof.head,proof.manifestId,proof.manifestHash,proof.account);
  db.prepare("INSERT INTO backup_binary_references VALUES (?,?,?,?,?,?,?)").run(proof.account,proof.binaryId,proof.checkpoint,proof.head,proof.cloudFileId,proof.binarySize,proof.binaryHash);
  db.exec("COMMIT");
  assert.ok(pending().some((row) => row.key0 === "later")); assert.equal(reference("b@example.com","pdf"),undefined);
  db.close(); db = new DatabaseSync(path); db.exec("PRAGMA foreign_keys=ON");
  assert.equal(reference("a@example.com","pdf").sha256,proof.binaryHash);
  assert.equal(db.prepare("SELECT checkpointId FROM backup_tracking_accounts WHERE accountScope='a@example.com'").get().checkpointId,proof.checkpoint);
  assert.equal(fingerprint("pdf").status,"DELETED");
  const saved = pending();
  db.exec("BEGIN"); db.exec("UPDATE backup_journal_state SET suppressionDepth=1,originEpoch=originEpoch+1 WHERE id=1");
  db.prepare(base.queries.invalidate).run({reason:"restore_requires_verified_baseline"});
  db.exec("UPDATE notes SET title='Restored Arabic العربية' WHERE id='existing'; UPDATE backup_journal_state SET suppressionDepth=0 WHERE id=1");
  db.exec("COMMIT"); assert.deepEqual(pending(),saved);
  assert.equal(db.prepare("SELECT trusted FROM backup_tracking_accounts WHERE accountScope='a@example.com'").get().trusted,0);
  db.exec("BEGIN; UPDATE backup_tracking_accounts SET trusted=1; ROLLBACK");
  assert.equal(db.prepare("SELECT trusted FROM backup_tracking_accounts WHERE accountScope='a@example.com'").get().trusted,0);
  assert.equal(db.prepare("PRAGMA integrity_check").get().integrity_check,"ok");
  assert.equal(db.prepare("PRAGMA foreign_key_check").all().length,0);
  console.log("PASS additive 33->34 migration; Arabic/rich-text preservation; unknown old bytes; zero/one/three pending capture; frozen payload; generation race; exact deletion without row read; same-ID equal-size byte replacement; fingerprint reuse without hashing; Trash; invalidation; account-scoped verified baseline/reference durability; Restore suppression; rollback; integrity.");
  console.log("Record reads: zero-change=0, one-note=1 (notes only), three objects=3. Capture hash reads=0.");
  console.log(`Disposable database: ${path}`);
} finally { db.close(); }
