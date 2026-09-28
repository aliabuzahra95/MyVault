import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { IsolatedGraphDrive } from './disposable-drive-broker.mjs';

function store(reply) {
  const s=new IsolatedGraphDrive();s.persist=()=>{};s.state.rootId='test-root';
  s.account={driveAccountId:'test-account'};
  s.state.reserved['test-file']={lineage:'lineage',role:'BINARY',created:false};
  s.calls=[];s.request=async(method,path)=>{s.calls.push({method,path});return reply(method,path);};
  return s;
}
test('unknown IDs fail before any provider request',async()=>{
  const s=store(()=>{throw Error('must not call');});
  await assert.rejects(()=>s.read('unallocated','lineage'));assert.equal(s.calls.length,0);
});
test('batched intended IDs are unique and reserved for exact lineage',async()=>{
  const s=store(()=>({status:200,bytes:Buffer.from('{"ids":["one","two","three"]}')}));
  assert.deepEqual(await s.reserveMany('lineage',3),['one','two','three']);assert.equal(s.calls.length,1);
  assert.ok(s.calls[0].path.includes('count=3'));assert.equal(s.state.reserved.one.lineage,'lineage');
  await assert.rejects(()=>s.reserveMany('lineage',3));
});
test('fresh listing still discovers siblings when earlier commit bytes are cached',async()=>{
  const bytes=Buffer.from('commit'),hash=createHash('sha256').update(bytes).digest('hex');
  let children=['test-file'];
  const metadata=id=>({id,size:String(bytes.length),parents:['test-root'],sha256Checksum:hash,appProperties:{lineage:'lineage',role:'COMMIT',sha256:hash}});
  const s=store((_method,path)=>path.startsWith('/drive/v3/files?')
    ?{status:200,bytes:Buffer.from(JSON.stringify({files:children.map(metadata)}))}:{status:200,bytes});
  s.fast=true;s.rootCheck=async()=>{};s.state.reserved['test-file'].role='COMMIT';
  s.state.reserved.sibling={lineage:'lineage',role:'COMMIT',created:false};
  assert.equal((await s.commits('lineage')).length,1);
  children.push('sibling');assert.equal((await s.commits('lineage')).length,2);
  assert.equal(s.calls.filter(c=>c.path.startsWith('/drive/v3/files?')).length,2);
  assert.equal(s.calls.filter(c=>c.path.includes('alt=media')).length,2);
});
test('warm cache does not bypass freshly checked root membership',async()=>{
  const bytes=Buffer.from('commit'),hash=createHash('sha256').update(bytes).digest('hex');
  const s=store(()=>({status:200,bytes:Buffer.from(JSON.stringify({id:'test-file',size:String(bytes.length),parents:['foreign-root'],sha256Checksum:hash,appProperties:{lineage:'lineage',role:'BINARY',sha256:hash}}))}));
  s.fast=true;s.cache.put({account:'test-account',root:'test-root',lineage:'lineage'}, {id:'test-file',size:bytes.length,sha256Checksum:hash,appProperties:{sha256:hash}},bytes);
  await assert.rejects(()=>s.read('test-file','lineage'));assert.equal(s.calls.length,1);
});
test('wrong lineage fails before any provider request',async()=>{
  const s=store(()=>{throw Error('must not call');});
  await assert.rejects(()=>s.read('test-file','another'));assert.equal(s.calls.length,0);
});
test('foreign parent cannot be read or marked owned',async()=>{
  const s=store(()=>({status:200,bytes:Buffer.from(JSON.stringify({id:'test-file',parents:['foreign-root'],appProperties:{lineage:'lineage'}}))}));
  await assert.rejects(()=>s.read('test-file','lineage'));assert.equal(s.calls.length,1);assert.equal(s.state.reserved['test-file'].created,false);
});
test('cleanup rechecks ownership even for a previously recorded creation',async()=>{
  const s=store(()=>({status:200,bytes:Buffer.from(JSON.stringify({id:'test-file',parents:['foreign-root'],appProperties:{lineage:'lineage'}}))}));
  s.state.reserved['test-file'].created=true;
  await assert.rejects(()=>s.cleanup());assert.ok(s.calls.every(c=>c.method==='GET'));
});
test('wrong bytes fail closed without stale fallback',async()=>{
  const expected=Buffer.from('original'),actual=Buffer.from('changed!');
  const s=store((_m,path)=>path.includes('alt=media')?{status:200,bytes:actual}:{status:200,bytes:Buffer.from(JSON.stringify({id:'test-file',parents:['test-root'],size:expected.length,appProperties:{lineage:'lineage',sha256:createHash('sha256').update(expected).digest('hex')}}))});
  await assert.rejects(()=>s.read('test-file','lineage'));
});
test('incomplete inventory cannot appear as an empty graph',async()=>{
  const s=store(()=>({status:200,bytes:Buffer.from('{"incompleteSearch":true,"files":[]}')}));s.rootCheck=async()=>{};
  await assert.rejects(()=>s.commits('lineage'));
});
