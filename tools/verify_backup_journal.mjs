import assert from "node:assert/strict";
import { readFileSync, mkdtempSync } from "node:fs";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";

const fixtureDirectory = process.env.MYVAULT_JOURNAL_TEST_DIR;
assert.ok(fixtureDirectory, "Set MYVAULT_JOURNAL_TEST_DIR to the targeted Kotlin test fixture directory.");
const fixture = JSON.parse(readFileSync(join(fixtureDirectory, "journal-sql.json"), "utf8"));
const directory = mkdtempSync(join(fixtureDirectory, "sqlite-disposable-"));
const path = join(directory, "journal-test.sqlite");
let db = new DatabaseSync(path);
const snapshots = new Map();
const groupEntities = new Map();
const tableFor = (file) => file === "pdf_annotation_geometry.json" ? "pdf_annotation_segments" : file.slice(0, -5);
const query = (name, params = {}) => db.prepare(fixture.queries[name]).all(params);
const run = (name, params = {}) => db.prepare(fixture.queries[name]).run(params);
const pending = (account = "a@example.com") => db.prepare("SELECT * FROM backup_pending_changes WHERE accountScope = ? ORDER BY recordGroup,key0,key1,key2").all(account);
const state = () => query("clock")[0];
const enroll = (account) => {
  db.prepare("INSERT OR IGNORE INTO backup_tracking_accounts VALUES (?,0,NULL,NULL,NULL,NULL,'baseline_required')").run(account);
  run("seedAccount", { scope: account });
};
const insert = (entity, id) => {
  const keys = entity.primaryKey.columnNames;
  const values = entity.fields.map((field) => {
    if (entity.tableName === "blocks" && field.columnName === "type") return "rich_text";
    if (entity.tableName === "blocks" && field.columnName === "content") return JSON.stringify({text: "English العربية", styleMarks: [{start: 0, end: 7, style: "bold"}], noteLinks: []});
    if (!field.notNull && !keys.includes(field.columnName)) return null;
    if (field.affinity === "INTEGER") return 1;
    if (field.affinity === "REAL") return 0.5;
    return keys.includes(field.columnName) ? id : `${id} English العربية — محفوظ`;
  });
  const columns = entity.fields.map((field) => `\`${field.columnName}\``);
  db.prepare(`INSERT INTO \`${entity.tableName}\` (${columns}) VALUES (${values.map(() => "?")})`).run(...values);
};

try {
  db.exec("PRAGMA foreign_keys = ON");
  for (const entity of fixture.schema32.database.entities) {
    db.exec(entity.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
    for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
  }
  for (const entity of fixture.schema32.database.entities) {
    for (const trigger of entity.contentSyncTriggers ?? []) db.exec(trigger);
  }
  for (const [file] of Object.entries(fixture.groups)) {
    const entity = fixture.schema32.database.entities.find((item) => item.tableName === tableFor(file));
    groupEntities.set(file, entity);
    insert(entity, "existing-id");
    snapshots.set(entity.tableName, JSON.stringify(db.prepare(`SELECT * FROM \`${entity.tableName}\``).all()));
  }
  db.exec("PRAGMA user_version = 32");
  db.exec("BEGIN");
  fixture.migration.forEach((sql) => db.exec(sql));
  db.exec("PRAGMA user_version = 33");
  db.exec("COMMIT");
  for (const [table, before] of snapshots) assert.equal(JSON.stringify(db.prepare(`SELECT * FROM \`${table}\``).all()), before, `Migration changed ${table}`);
  assert.equal(pending("local-unassigned").length, 0, "Migration must not scan/dirty existing rows");
  assert.equal(db.prepare("SELECT trusted FROM backup_tracking_accounts").get().trusted, 0);
  Object.values(fixture.triggers).flat().forEach((sql) => db.exec(sql));
  enroll("a@example.com"); enroll("b@example.com");

  for (const [file, entity] of groupEntities) {
    insert(entity, "new-id");
    const rows = pending().filter((row) => row.recordGroup === file);
    assert.equal(rows.length, 1, file);
    assert.equal(rows[0].operation, "UPSERT");
    assert.equal(rows[0].key0, "new-id");
  }
  assert.equal(pending().length, 20);
  // Genuine user deletes are exact row identities, including composite identities.
  for (const [file, fields] of Object.entries(fixture.groups)) {
    if (file === "pdf_annotation_geometry.json") continue; // verified separately with its parent alive
    const table = tableFor(file);
    db.prepare(`DELETE FROM \`${table}\` WHERE \`${fields[0]}\` = ?`).run("new-id");
    const change = pending().find((row) => row.recordGroup === file && row.key0 === "new-id");
    assert.equal(change.operation, "DELETE", file);
  }
  // Annotation deletion cascades exact segment rows; neither existing parent nor geometry disappears.
  const segment = pending().find((row) => row.recordGroup === "pdf_annotation_geometry.json" && row.key0 === "new-id");
  assert.equal(segment.operation, "DELETE");
  assert.equal(segment.key1, "1");
  for (const [table, before] of snapshots) assert.equal(JSON.stringify(db.prepare(`SELECT * FROM \`${table}\``).all()), before, `Deletion damaged ${table}`);
  const count = pending().length;
  db.prepare("DELETE FROM notes WHERE id = ?").run("absent-id");
  assert.equal(pending().length, count, "Absence must never synthesize a deletion");

  const noteEntity = groupEntities.get("notes.json");
  insert(noteEntity, "editing-note");
  for (let i = 0; i < 20; i++) db.prepare("UPDATE notes SET title = ?, bodyPlainText = ?, folderId = ? WHERE id = ?").run(`Renamed ${i}`, `English العربية ${i}`, "existing-id", "editing-note");
  const edited = pending().filter((row) => row.recordGroup === "notes.json" && row.key0 === "editing-note");
  assert.equal(edited.length, 1);
  assert.equal(edited[0].operation, "UPSERT");
  const beforeNoop = state().generation;
  db.exec("UPDATE notes SET title = title WHERE id = 'editing-note'");
  assert.equal(state().generation, beforeNoop);
  const captured = state().generation;
  db.exec("UPDATE notes SET title = 'New edit after capture' WHERE id = 'editing-note'");
  run("ack", { scope: "a@example.com", generation: captured });
  assert.equal(pending().length, 1);
  assert.ok(pending()[0].generation > captured);
  assert.ok(pending("b@example.com").length > 1, "Another account must not be acknowledged");
  const lastGeneration = state().generation;
  // Failed staging/upload has no acknowledgement; simply reopen the durable database.
  db.close(); db = new DatabaseSync(path); db.exec("PRAGMA foreign_keys = ON");
  assert.equal(pending().length, 1);
  assert.equal(pending()[0].generation, lastGeneration);
  run("ack", { scope: "a@example.com", generation: lastGeneration });
  assert.equal(pending().length, 0);
  assert.ok(pending("b@example.com").length > 1);
  enroll("c@example.com");
  assert.ok(pending("c@example.com").length > 1, "Pre-enrolment changes must survive");
  assert.equal(db.prepare("SELECT trusted FROM backup_tracking_accounts WHERE accountScope=?").get("c@example.com").trusted, 0);

  db.prepare("UPDATE notes SET deletedAt = 123 WHERE id = ?").run("editing-note");
  assert.equal(pending().find((row) => row.key0 === "editing-note").operation, "UPSERT", "Trash is not permanent deletion");
  db.exec("BEGIN");
  db.exec("UPDATE notes SET bodyPlainText = 'Should roll back' WHERE id = 'editing-note'");
  const transactionGeneration = state().generation;
  db.exec("ROLLBACK");
  assert.ok(state().generation < transactionGeneration, "Journal and entity writes must roll back together");
  assert.ok(db.prepare("SELECT bodyPlainText FROM notes WHERE id='editing-note'").get().bodyPlainText.includes("العربية"));

  const beforeRestore = pending();
  db.exec("BEGIN");
  db.exec("UPDATE backup_journal_state SET suppressionDepth = suppressionDepth + 1, originEpoch = originEpoch + 1 WHERE id = 1");
  run("invalidate", { reason: "restore_requires_verified_baseline" });
  db.exec("UPDATE notes SET title = 'Restored fixture' WHERE id = 'editing-note'");
  db.exec("UPDATE backup_journal_state SET suppressionDepth = suppressionDepth - 1 WHERE id = 1");
  db.exec("COMMIT");
  assert.deepEqual(pending(), beforeRestore, "Restore must not echo as user changes");
  assert.equal(state().suppressionDepth, 0);
  assert.equal(db.prepare("SELECT count(*) AS n FROM backup_tracking_accounts WHERE trusted != 0").get().n, 0);
  run("tick"); run("settingsDirty");
  db.exec("UPDATE backup_journal_state SET settingsToken = 'interrupted' WHERE id = 1");
  db.close(); db = new DatabaseSync(path);
  assert.equal(state().settingsToken, "interrupted");
  run("tick"); run("settingsDirty"); run("invalidate", { reason: "interrupted_preferences_require_baseline" });
  db.exec("UPDATE backup_journal_state SET settingsToken = NULL WHERE id = 1");
  assert.equal(pending().filter((row) => row.recordGroup === "settings.json").length, 1);
  assert.equal(pending().find((row) => row.recordGroup === "settings.json").operation, "UPSERT");
  // Keys containing delimiters must remain separate structured identities, never string tokens.
  insert(noteEntity, "composite:a:b"); insert(noteEntity, "composite:a");
  insert(groupEntities.get("tags.json"), "c"); insert(groupEntities.get("tags.json"), "b:c");
  db.prepare("INSERT INTO note_tags (noteId,tagName) VALUES (?,?)").run("composite:a:b", "c");
  db.prepare("INSERT INTO note_tags (noteId,tagName) VALUES (?,?)").run("composite:a", "b:c");
  const refs = () => pending().filter((row) => row.recordGroup === "note_tags.json" && row.key0.startsWith("composite:"));
  assert.equal(refs().length, 2);
  db.prepare("DELETE FROM note_tags WHERE noteId=? AND tagName=?").run("composite:a:b", "c");
  assert.equal(refs().find((row) => row.key0 === "composite:a:b").operation, "DELETE");
  assert.equal(refs().find((row) => row.key0 === "composite:a").operation, "UPSERT");
  db.prepare("INSERT INTO note_tags (noteId,tagName) VALUES (?,?)").run("composite:a:b", "c");
  assert.equal(refs().length, 2);
  assert.equal(refs().find((row) => row.key0 === "composite:a:b").operation, "UPSERT");
  assert.equal(db.prepare("PRAGMA integrity_check").get().integrity_check, "ok");
  assert.equal(db.prepare("PRAGMA foreign_key_check").all().length, 0);
  console.log("PASS schema 32 -> 33 preservation; all 20 groups; coalescing; no-op update; exact/composite/cascade deletion; absence; Trash; durable reopen; account isolation; generation race; unchanged ack; transaction rollback; Restore suppression; interrupted settings intent; integrity. Disposable SQLite only.");
  console.log(`Disposable database: ${path}`);
} finally { db.close(); }
