import http from 'node:http';
import { randomUUID, randomBytes, createHash } from 'node:crypto';
import { writeFileSync, readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { performance } from 'node:perf_hooks';
import { VerifiedByteCache } from './verified-byte-cache.mjs';

const dir = process.env.MYVAULT_GRAPH_EVIDENCE_DIR || dirname(fileURLToPath(import.meta.url));
const evidence = join(dir, 'authenticated-drive-evidence.json');
const configFile = join(dir, '.private', 'broker-config.json');
const fields = 'id,name,mimeType,size,parents,appProperties,trashed,sha256Checksum';
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const idPattern = /^[A-Za-z0-9_-]+$/;
let token, tokenUntil = 0;
async function disposableAccessToken() {
  if(!process.env.MYVAULT_GRAPH_AUTH_HELPER) throw Error('Explicit private disposable OAuth helper required');
  const auth = await import(process.env.MYVAULT_GRAPH_AUTH_HELPER);
  return auth.disposableAccessToken();
}

export class IsolatedGraphDrive {
  constructor() {
    this.cache = new VerifiedByteCache(); this.fast = false;
    this.state = { name: `MYVAULT-GRAPH-DISPOSABLE-${Date.now()}-${randomUUID()}`, rootId: null, lineageId: randomUUID(),
      scope: 'https://www.googleapis.com/auth/drive.file', reserved: {}, requests: [], tests: [], cleanup: [], marker: 'setup' };
  }
  persist() { writeFileSync(evidence, JSON.stringify(this.state, null, 2), { mode: 0o600 }); }
  async request(method, path, role, objectId = null, body = null, type = null) {
    const parsed=new URL(path,'https://www.googleapis.com');
    const match=parsed.pathname.match(/^\/drive\/v3\/files\/([A-Za-z0-9_-]+)$/);
    const recognised=method==='GET' && (parsed.pathname==='/drive/v3/about' || parsed.pathname==='/drive/v3/files/generateIds');
    if(!recognised) {
      if(method==='GET' && parsed.pathname==='/drive/v3/files') {
        if(parsed.searchParams.get('q')!==`'${this.state.rootId}' in parents and trashed=false`)throw new Error('Unscoped listing forbidden');
      } else if(method==='POST' && parsed.pathname==='/drive/v3/files') {
        const m=JSON.parse(body);
        if(m.id!==this.state.rootId || m.name!==this.state.name || m.parents || m.mimeType!=='application/vnd.google-apps.folder')throw new Error('Only the new disposable root may be created');
      } else if(method==='POST' && parsed.pathname==='/upload/drive/v3/files') {
        const m=JSON.parse(body.toString('utf8').split('\r\n')[3]);
        const owned=this.entry(m.id,m.appProperties?.lineage);
        if(m.parents?.length!==1 || m.parents[0]!==this.state.rootId || owned.role!==m.appProperties.role)throw new Error('Upload outside disposable root forbidden');
      } else if(match && ['GET','DELETE'].includes(method)) {
        const id=match[1];
        if(!(id in this.state.reserved))throw new Error('Unallocated Drive ID forbidden');
        if(method==='DELETE' && !this.state.reserved[id].created)throw new Error('Deletion requires verified disposable ownership');
      } else throw new Error('Unexpected Drive operation forbidden');
    }
    const authStart = performance.now();
    const refreshed = performance.now() >= tokenUntil;
    if (refreshed) { token = await disposableAccessToken(); tokenUntil = performance.now() + 3000000; }
    const log = { method, path, role, objectId, marker: this.state.marker, started: Date.now(), uploadBytes: 0, readbackBytes: 0 };
    log.authMs = performance.now() - authStart; log.refreshed = refreshed; log.stage = this.stage || 'harness';
    const began = performance.now();
    this.state.requests.push(log); this.persist();
    log.loggingBeforeMs = performance.now() - began;
    const fetchStart = performance.now();
    const response = await fetch(`https://www.googleapis.com${path}`, { method, headers: { Authorization: `Bearer ${token}`, ...(type ? { 'Content-Type': type } : {}) },
      body, signal: AbortSignal.timeout(60000), redirect: 'error' });
    const bytes = Buffer.from(await response.arrayBuffer());
    log.status = response.status; log.elapsedMs = performance.now() - began;
    log.providerMs = performance.now() - fetchStart;
    if (path.includes('alt=media')) log.readbackBytes = bytes.length;
    const logStart = performance.now(); this.persist(); log.loggingAfterMs = performance.now()-logStart;
    return { status: response.status, bytes, log };
  }
  good(response) { if (response.status < 200 || response.status >= 300) throw new Error(`Disposable Drive HTTP ${response.status}`); return response; }
  async reserveMany(lineage, count) {
    if (!idPattern.test(lineage)) throw new Error('Invalid test lineage');
    if(!Number.isInteger(count) || count < 1 || count > 1000) throw Error('Invalid ID batch');
    const r = this.good(await this.request('GET', `/drive/v3/files/generateIds?count=${count}&space=drive&type=files`, 'RESERVE'));
    const ids = JSON.parse(r.bytes).ids;
    if(!Array.isArray(ids) || ids.length !== count || new Set(ids).size !== count || ids.some(id=>!idPattern.test(id || '') || id in this.state.reserved)) throw new Error('Unexpected generated Drive IDs');
    for(const id of ids) this.state.reserved[id] = { lineage, role: null, created: false };
    this.persist(); return ids;
  }
  async reserve(lineage) { return (await this.reserveMany(lineage,1))[0]; }
  entry(id, lineage) {
    if (!idPattern.test(id || '') || !(id in this.state.reserved) || this.state.reserved[id].lineage !== lineage) throw new Error('ID is outside disposable allowlist');
    return this.state.reserved[id];
  }
  async initialise() {
    const about = this.good(await this.request('GET', '/drive/v3/about?fields=user(permissionId,emailAddress)', 'IDENTITY'));
    const user = JSON.parse(about.bytes).user;
    if (!user?.permissionId || !user?.emailAddress) throw new Error('Verified Drive account identity missing');
    this.account = { accountScope: user.emailAddress.trim().toLowerCase(), driveAccountId: user.permissionId, lineageId: this.state.lineageId };
    const id = await this.reserve('root'); this.state.rootId = id; this.persist();
    const r = this.good(await this.request('POST', '/drive/v3/files?fields=id,name,mimeType', 'ROOT', id,
      JSON.stringify({ id, name: this.state.name, mimeType: 'application/vnd.google-apps.folder' }), 'application/json'));
    if (JSON.parse(r.bytes).id !== id) throw new Error('Intended root ID changed');
    Object.assign(this.state.reserved[id], { created: true, role: 'ROOT' }); this.persist();
    await this.rootCheck();
  }
  async rootCheck() {
    const r = this.good(await this.request('GET', `/drive/v3/files/${this.state.rootId}?fields=${encodeURIComponent(fields)}`, 'ROOT', this.state.rootId));
    const m = JSON.parse(r.bytes);
    if (m.id !== this.state.rootId || m.name !== this.state.name || m.mimeType !== 'application/vnd.google-apps.folder' || m.trashed) throw new Error('Disposable root is unavailable');
  }
  async read(id, lineage) {
    const entry = this.entry(id, lineage);
    const r = await this.request('GET', `/drive/v3/files/${id}?fields=${encodeURIComponent(fields)}`, entry.role || 'INTENDED', id);
    if (r.status === 404) return null;
    const m = JSON.parse(this.good(r).bytes);
    if(m.id !== id)throw Error('Requested immutable ID changed');
    return this.readMetadata(m,lineage);
  }
  async readMetadata(m,lineage) {
    const id=m.id,entry=this.entry(id,lineage);
    if (m.id !== id || m.trashed || m.parents?.length !== 1 || m.parents[0] !== this.state.rootId || m.appProperties?.lineage !== lineage)
      throw new Error('Object membership is not the disposable root/lineage');
    if(entry.role && m.appProperties.role !== entry.role)throw Error('Immutable object role changed');
    const context={account:this.account.driveAccountId,root:this.state.rootId,lineage};
    const cached=this.fast ? this.cache.get(context,m) : null;
    if(cached) { this.state.cacheHits=(this.state.cacheHits||0)+1; return cached; }
    entry.created=true; this.persist();
    const media = this.good(await this.request('GET', `/drive/v3/files/${id}?alt=media`, m.appProperties.role, id));
    if (media.bytes.length !== Number(m.size) || sha(media.bytes) !== m.appProperties.sha256) throw new Error('Disposable object hash/size mismatch');
    if(m.sha256Checksum) {
      if(m.sha256Checksum!==sha(media.bytes)) throw Error('Provider SHA-256 mismatch');
      this.state.providerSha256Verified=(this.state.providerSha256Verified||0)+1;
    }
    if(this.fast)this.cache.put(context,m,media.bytes);
    if(media.log)media.log.verified = true; this.persist(); return media.bytes;
  }
  async create(id, lineage, role, bytes, forceDuplicate = false) {
    const entry = this.entry(id, lineage);
    if (!['METADATA','BINARY','CHECKPOINT','DELTA','COMMIT','PROBE'].includes(role)) throw new Error('Invalid immutable role');
    await this.rootCheck();
    if (!forceDuplicate && !this.fast) {
      const prior = await this.read(id, lineage);
      if (prior) { if (!prior.equals(bytes)) throw new Error('Conflicting intended object bytes'); return; }
    }
    if (entry.role && entry.role !== role) throw new Error('Object role conflict');
    const boundary = `graph_${randomUUID()}`;
    const metadata = { id, name: `${role.toLowerCase()}-${id}`, parents: [this.state.rootId],
      appProperties: { lineage, role, sha256: sha(bytes) } };
    const body = Buffer.concat([Buffer.from(`--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}\r\n--${boundary}\r\nContent-Type: application/octet-stream\r\n\r\n`), bytes, Buffer.from(`\r\n--${boundary}--\r\n`)]);
    entry.role = role; this.persist();
    const r = await this.request('POST', '/upload/drive/v3/files?uploadType=multipart&fields=id', role, id, body, `multipart/related; boundary=${boundary}`);
    if(r.log)r.log.uploadBytes = bytes.length; this.persist();
    if (r.status !== 409) { this.good(r); if (JSON.parse(r.bytes).id !== id) throw new Error('Intended file ID changed'); }
    if(!this.fast || forceDuplicate) {
      const actual = await this.read(id, lineage);
      if (!actual?.equals(bytes)) throw new Error('Conflicting intended object bytes');
    }
  }
  async commits(lineage) {
    await this.rootCheck();
    const commits = []; let pageToken;
    do {
      const q = `'${this.state.rootId}' in parents and trashed=false`;
      const query = new URLSearchParams({ q, spaces: 'drive', pageSize: '1000', fields: `nextPageToken,incompleteSearch,files(${fields})`, ...(pageToken ? { pageToken } : {}) });
      const r = this.good(await this.request('GET', `/drive/v3/files?${query}`, 'DISCOVERY'));
      const data = JSON.parse(r.bytes);
      if (data.incompleteSearch || !Array.isArray(data.files)) throw new Error('Incomplete discovery');
      for (const m of data.files) {
        const known = this.entry(m.id, m.appProperties?.lineage);
        if (known.role !== m.appProperties.role || m.parents?.[0] !== this.state.rootId) throw new Error('Ambiguous disposable inventory');
        if (m.appProperties.lineage === lineage && m.appProperties.role === 'COMMIT') {
          const bytes = this.fast ? await this.readMetadata(m,lineage) : await this.read(m.id, lineage);
          commits.push({ objectRef: { cloudFileId: m.id, sha256: m.appProperties.sha256, size: Number(m.size) }, bytes: bytes.toString('base64') });
        }
      }
      pageToken = data.nextPageToken;
    } while (pageToken);
    return commits;
  }
  async preflight() {
    this.state.marker = 'provider-preflight';
    const id = await this.reserve('preflight');
    const bytes = Buffer.from('immutable intended object العربية');
    await this.create(id, 'preflight', 'PROBE', bytes);
    await this.create(id, 'preflight', 'PROBE', bytes, true);
    let rejected = false;
    try { await this.create(id, 'preflight', 'PROBE', Buffer.from('conflicting bytes'), true); } catch (e) { if (e.message === 'Conflicting intended object bytes') rejected = true; else throw e; }
    if (!rejected || !(await this.read(id, 'preflight')).equals(bytes)) throw new Error('Duplicate create did not preserve immutable bytes');
    this.state.tests.push({ test: 'pre-generated-ID-and-duplicate-create', passed: true }); this.persist();
  }
  async cleanup() {
    this.state.marker = 'cleanup';
    const children=Object.entries(this.state.reserved).filter(([id])=>id!==this.state.rootId);
    const remove=async([id,entry])=>{
      const found = await this.read(id, entry.lineage);
      if (!found) return;
      const r = await this.request('DELETE', `/drive/v3/files/${id}`, entry.role, id);
      if (![204,404].includes(r.status)) throw new Error(`Disposable child cleanup failed: ${id}`);
      const check = await this.request('GET', `/drive/v3/files/${id}?fields=id`, 'CLEANUP_VERIFY', id);
      if (check.status !== 404) throw new Error(`Disposable child still exists: ${id}`);
      this.state.cleanup.push({ id, deleted: true }); this.persist();
    };
    // Independent exact-ID cleanup only. Await every outcome before considering root deletion.
    for(let offset=0;offset<children.length;offset+=4) {
      const results=await Promise.allSettled(children.slice(offset,offset+4).map(remove));
      const failures=results.filter(r=>r.status==='rejected');
      if(failures.length)throw new AggregateError(failures.map(r=>r.reason),'Disposable child cleanup failed; root retained');
    }
    await this.rootCheck();
    if((await this.commits('__cleanup_inventory__')).length!==0)throw new Error('Disposable root still contains unexpected commits');
    const root = await this.request('DELETE', `/drive/v3/files/${this.state.rootId}`, 'ROOT', this.state.rootId);
    if (root.status !== 204) throw new Error('Disposable root cleanup failed');
    const checked = await this.request('GET', `/drive/v3/files/${this.state.rootId}?fields=id`, 'CLEANUP_VERIFY', this.state.rootId);
    if (checked.status !== 404) throw new Error('Disposable root still exists');
    this.state.cleanup.push({ id: this.state.rootId, deleted: true }); this.state.finished = true; this.persist();
  }
}

async function serve() {
  if(!process.env.MYVAULT_GRAPH_EVIDENCE_DIR) throw Error('Explicit private evidence directory required');
  const store = new IsolatedGraphDrive();
  if(process.env.MYVAULT_BROKER_RESUME === '1') {
    store.state=JSON.parse(readFileSync(evidence));
    if(store.state.finished || !store.state.rootId || !store.state.name.startsWith('MYVAULT-GRAPH-DISPOSABLE-'))throw Error('Invalid disposable resume');
    const prior=JSON.parse(readFileSync(configFile));store.account={accountScope:prior.accountScope,driveAccountId:prior.driveAccountId,lineageId:store.state.lineageId};
    const identity=JSON.parse(store.good(await store.request('GET','/drive/v3/about?fields=user(permissionId)','IDENTITY')).bytes);
    if(identity.user?.permissionId!==store.account.driveAccountId)throw Error('Disposable account changed');
    store.fast=true;await store.rootCheck();
  }
  try { if(!store.state.rootId) { await store.initialise(); await store.preflight(); } }
  catch (e) { store.state.error = e.message; store.persist(); if (store.state.rootId) await store.cleanup(); throw e; }
  const nonce = randomBytes(32).toString('hex');
  const server = http.createServer(async (req, res) => {
    res.setHeader('Connection', 'close'); res.setHeader('Cache-Control', 'no-store');
    const reply = (code, data) => { const body = Buffer.from(JSON.stringify(data)); res.writeHead(code, { 'Content-Type':'application/json', 'Content-Length':body.length }); res.end(body); };
    try {
      if (req.method !== 'POST' || req.url !== '/call' || req.headers['x-test-nonce'] !== nonce) return reply(403, { error:'Local test authorization required' });
      const chunks = []; let size = 0;
      for await (const chunk of req) { size += chunk.length; if (size > 16000000) throw new Error('Test body too large'); chunks.push(chunk); }
      const data = JSON.parse(Buffer.concat(chunks));
      store.stage = data.stage;
      const lineage = data.lineage || store.state.lineageId;
      let value;
      if (data.action === 'reserve') value = await store.reserve(lineage);
      else if(data.action==='reserveMany') value=await store.reserveMany(lineage,data.count);
      else if (data.action === 'read') { const bytes = await store.read(data.id, lineage); value = bytes ? bytes.toString('base64') : null; }
      else if (data.action === 'create') { await store.create(data.id, lineage, data.role, Buffer.from(data.bytes, 'base64')); value = true; }
      else if (data.action === 'commits') value = await store.commits(lineage);
      else if (data.action === 'marker') { store.state.marker = data.name; store.persist(); value = { requests: store.state.requests.length, time: Date.now() }; }
      else if(data.action==='clearCache') {store.cache.clear();value=true;}
      else if(data.action==='preflight') {await store.preflight();value=true;}
      else if (data.action === 'cleanup') { await store.cleanup(); value = true; }
      else throw new Error('Unsupported disposable action');
      reply(200, { value });
    } catch (e) { reply(409, { error: e.message }); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  writeFileSync(configFile, JSON.stringify({ port: server.address().port, nonce, ...store.account, dbName: `graph-drive-disposable-${randomUUID()}.db`, pid:process.pid }), { mode:0o600 });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv[2] === 'serve') await serve();
  else if (process.argv[2] === 'start') {
    const child = spawn(process.execPath, [fileURLToPath(import.meta.url), 'serve'], { detached:true, stdio:'ignore' }); child.unref();
    for(let n=0;n<1200;n++) {
      if(existsSync(configFile)) { const c=JSON.parse(readFileSync(configFile)); if(c.pid===child.pid) { console.log('Disposable Drive preflight passed; broker ready.'); process.exit(0); } }
      if(existsSync(evidence)) { const s=JSON.parse(readFileSync(evidence)); if(s.error) { console.log(`STOP: ${s.error}`); process.exit(1); } }
      await new Promise(resolve=>setTimeout(resolve,100));
    }
    throw new Error('Disposable broker did not become ready; inspect redacted evidence.');
  }
}
