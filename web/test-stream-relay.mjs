import assert from 'node:assert/strict';
import fs from 'node:fs';
import {EventEmitter} from 'node:events';
import {Readable} from 'node:stream';
let requests=0;
globalThis.relayLookup=async host=>[{address:host==='private.example'?'127.0.0.1':'8.8.8.8'}];
globalThis.relayRequest=(url,options,callback)=>{
 requests++;const request=new EventEmitter();request.setTimeout=()=>{};request.destroy=()=>{};
 request.end=()=>{let body=Buffer.from([0x47,0,0,0]);let headers={'content-type':'video/mp2t'},status=200;
  if(url.pathname.endsWith('.m3u8')){body=Buffer.from('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\nsegment.ts\n');headers={'content-type':'application/vnd.apple.mpegurl'}}
  if(url.pathname==='/redirect'){headers={location:'http://public.example/live.ts'};status=302}
  const response=Readable.from([body]);response.headers=headers;response.statusCode=status;callback(response);
 };return request;
};
let code=fs.readFileSync('stream-relay.mjs','utf8').replace("import {lookup} from 'node:dns/promises';","const lookup=globalThis.relayLookup;").replace("import {request as httpRequest} from 'node:http';","const httpRequest=globalThis.relayRequest;").replace("import {request as httpsRequest} from 'node:https';","const httpsRequest=globalThis.relayRequest;");
fs.writeFileSync('/tmp/relay-testable.mjs',code);
const {handleRelay,publicIPv4,parseSource}=await import('/tmp/relay-testable.mjs');
for(const ip of ['127.0.0.1','10.0.0.1','169.254.169.254','192.168.1.1','172.16.0.1','224.0.0.1','0.0.0.0'])assert.equal(publicIPv4(ip),false);
assert.equal(publicIPv4('8.8.8.8'),true);assert.throws(()=>parseSource('http://public.example/test.ts',false));assert.throws(()=>parseSource('file:///tmp/test',true));
const base='https://inter-tv-prueba-production.up.railway.app';
const create=(url,insecureConfirmed=false,cookie='')=>handleRelay(new Request(base+'/api/playback',{method:'POST',headers:{origin:base,'content-type':'application/json',cookie},body:JSON.stringify({url,insecureConfirmed})}));
let result=await create('http://public.example/live.ts');assert.equal(result.status,400);assert.equal(requests,0);
result=await handleRelay(new Request(base+'/api/playback',{method:'POST',headers:{origin:'https://other.example'},body:'{}'}));assert.equal(result.status,403);
result=await create('http://public.example/list.m3u8',true);assert.equal(result.status,200);assert.equal(requests,0);
let cookie=result.headers.get('set-cookie').split(';')[0],resource=(await result.json()).url;
assert.equal((await handleRelay(new Request(base+resource))).status,403);
result=await handleRelay(new Request(base+resource,{headers:{cookie}}));assert.equal(result.status,200);const manifest=await result.text();assert(!manifest.includes('http:'));assert(manifest.includes('URI="/api/stream/'));const segment=manifest.split('\n').find(l=>l.startsWith('/api/stream/'));
result=await handleRelay(new Request(base+segment,{headers:{cookie}}));assert.equal(result.status,200);assert.equal((await result.arrayBuffer()).byteLength,4);
result=await create('https://private.example/live.ts',false,cookie);resource=(await result.json()).url;const before=requests;result=await handleRelay(new Request(base+resource,{headers:{cookie}}));assert.equal(result.status,502);assert.equal(requests,before);
result=await create('https://public.example/redirect',false,cookie);resource=(await result.json()).url;result=await handleRelay(new Request(base+resource,{headers:{cookie}}));assert.equal(result.status,502);
console.log('PASS relay: explicit HTTP consent, cookie isolation, origin checks, public DNS validation, HLS URI rewriting, TS streaming, no HTTPS downgrade');
