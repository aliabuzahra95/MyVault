import { readFileSync, writeFileSync } from 'node:fs';
const [evidenceFile, beforeFile, afterFile, output] = process.argv.slice(2);
if(!output) throw Error('Provide redacted request evidence, before/after captures and output JSON');
const evidence=JSON.parse(readFileSync(evidenceFile));
const sum=(xs)=>xs.reduce((a,b)=>a+b,0);
const stats=xs=>({median:[...xs].sort((a,b)=>a-b)[Math.floor(xs.length/2)],min:Math.min(...xs),max:Math.max(...xs)});
function categorize(r) {
  if(r.path.includes('/generateIds'))return 'generateIds';
  if(r.method==='POST')return 'createUpload';
  if(r.path.includes('alt=media'))return 'download';
  if(r.path.startsWith('/drive/v3/files?'))return 'filesList';
  return 'metadataGet';
}
const phases={};
for(const file of [beforeFile,afterFile]) {
  const capture=JSON.parse(readFileSync(file));
  const runs=capture.results.map(run=>{
    const requests=evidence.requests.slice(run.requestStart,run.requestEnd);
    const categories={};const networkStages={};const spans={};
    for(const r of requests) {
      const category=categorize(r);categories[category]=(categories[category]||0)+1;
      networkStages[r.stage]=(networkStages[r.stage]||0)+r.elapsedMs;
    }
    for(const s of run.spans)spans[s.name]=(spans[s.name]||0)+(s.endNanos-s.startNanos)/1e6;
    return {...run,requests:requests.length,categories,missingIdProbes:requests.filter(r=>r.status===404).length,
      uploadedBytes:sum(requests.map(r=>r.uploadBytes||0)),downloadedBytes:sum(requests.map(r=>r.readbackBytes||0)),
      requestMs:sum(requests.map(r=>r.elapsedMs)),authMs:sum(requests.map(r=>r.authMs||0)),
      tokenRefreshes:requests.filter(r=>r.refreshed).length,networkStages,phaseMs:spans,
      localAndBridgeMs:run.coreMs-sum(requests.map(r=>r.elapsedMs))};
  });
  const scenarios={};
  for(const scenario of ['zero','one-note','three','binary','metadata-only']) {
    const selected=runs.filter(r=>r.name.endsWith('-'+scenario));
    scenarios[scenario]={coreMs:stats(selected.map(r=>r.coreMs)),harnessMs:stats(selected.map(r=>r.harnessMs)),
      requests:selected.map(r=>r.requests),uploads:selected.map(r=>r.uploadedBytes),downloads:selected.map(r=>r.downloadedBytes),
      captureMs:stats(selected.map(r=>r.phaseMs['journal.capture']||0))};
  }
  const cold=capture.coldCache;
  const coldRequests=cold ? evidence.requests.slice(cold.requestStart,cold.requestEnd) : [];
  phases[capture.phase]={runs,scenarios,setupMs:capture.setupMs??null,measuredPhaseMs:capture.phaseMs??null,
    coldCache:cold ? {...cold,requests:coldRequests.length,downloadedBytes:sum(coldRequests.map(r=>r.readbackBytes||0))} : null};
}
writeFileSync(output,JSON.stringify({phases},null,2));
console.log(JSON.stringify(Object.fromEntries(Object.entries(phases).map(([k,v])=>[k,v.scenarios])),null,2));
