import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { performance } from 'node:perf_hooks';
import { IsolatedGraphDrive } from './disposable-drive-broker.mjs';

const dir=process.env.MYVAULT_GRAPH_EVIDENCE_DIR;
if(!dir || !process.env.MYVAULT_GRAPH_AUTH_HELPER)throw Error('Explicit protected disposable configuration required');
const config=JSON.parse(readFileSync(join(dir,'.private/broker-config.json')));
const state=JSON.parse(readFileSync(join(dir,'authenticated-drive-evidence.json')));
if(state.finished || !state.name.startsWith('MYVAULT-GRAPH-DISPOSABLE-') || !state.rootId || state.reserved[state.rootId]?.role!=='ROOT')throw Error('Invalid disposable cleanup identity');
const command=execFileSync('ps',['-p',String(config.pid),'-o','command='],{encoding:'utf8'});
if(!command.includes('/tools/backup-graph/disposable-drive-broker.mjs serve'))throw Error('Unexpected process; refusing cleanup');
process.kill(config.pid,'SIGTERM');
const store=new IsolatedGraphDrive();store.state=state;store.account=config;store.fast=true;store.stage='cleanup';
const identity=JSON.parse(store.good(await store.request('GET','/drive/v3/about?fields=user(permissionId)','IDENTITY')).bytes);
if(identity.user?.permissionId!==config.driveAccountId)throw Error('Cleanup account differs; all objects retained');
const start=performance.now(), requestStart=store.state.requests.length;
await store.cleanup();
const result={rootName:state.name,rootId:state.rootId,deleted:state.cleanup.length,verifiedAbsent:state.finished,
  elapsedMs:performance.now()-start,requests:store.state.requests.length-requestStart};
writeFileSync(join(dir,'cleanup-result.json'),JSON.stringify(result,null,2),{mode:0o600});
console.log(JSON.stringify(result));
