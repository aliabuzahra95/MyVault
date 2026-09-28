import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { VerifiedByteCache } from './verified-byte-cache.mjs';
const bytes = Buffer.from('English الْعَرَبِيَّة');
const hash = createHash('sha256').update(bytes).digest('hex');
const context = { account: 'disposable', root: 'root', lineage: 'lineage' };
const metadata = { id: 'id', size: String(bytes.length), sha256Checksum: hash, appProperties: { sha256: hash } };
test('fresh strong digest can reuse independently verified bytes', () => {
  const c = new VerifiedByteCache(); assert.equal(c.get(context,metadata),null); c.put(context,metadata,bytes);
  assert.deepEqual(c.get(context,metadata),bytes);
});
test('missing provider digest falls back, never trusting appProperties alone', () => {
  const c = new VerifiedByteCache(); c.put(context,metadata,bytes);
  assert.equal(c.get(context,{...metadata,sha256Checksum:undefined}),null);
});
test('changed provider hash invalidates cached bytes', () => {
  const c = new VerifiedByteCache(); c.put(context,metadata,bytes);
  assert.equal(c.get(context,{...metadata,sha256Checksum:'f'.repeat(64)}),null);
});
test('same-size changed bytes cannot populate cache', () => {
  const c = new VerifiedByteCache(); assert.throws(()=>c.put(context,metadata,Buffer.alloc(bytes.length)));
});
test('account, root and lineage cannot share receipts', () => {
  const c = new VerifiedByteCache(); c.put(context,metadata,bytes);
  for(const key of ['account','root','lineage']) assert.equal(c.get({...context,[key]:'other'},metadata),null);
});
test('capacity is bounded and clear/restart safely falls back', () => {
  const c = new VerifiedByteCache(bytes.length); c.put(context,metadata,bytes);
  c.put(context,{...metadata,id:'new'},bytes); assert.equal(c.get(context,metadata),null);
  c.clear(); assert.equal(c.get(context,{...metadata,id:'new'}),null); assert.equal(c.bytes,0);
});
test('returned bytes cannot corrupt verified cache', () => {
  const c = new VerifiedByteCache(); c.put(context,metadata,bytes); c.get(context,metadata).fill(0);
  assert.deepEqual(c.get(context,metadata),bytes);
});
