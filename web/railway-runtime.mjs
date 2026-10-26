import {lookup} from 'node:dns/promises';
import {request as httpRequest} from 'node:http';
import {request as httpsRequest} from 'node:https';
import {Readable} from 'node:stream';

const APP_ORIGIN='https://inter-tv-prueba-production.up.railway.app';
const sessions=new Map();
const noStore={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'};
export function publicIPv4(address){
 const p=address.split('.').map(Number);
 return p.length===4&&p.every(n=>Number.isInteger(n)&&n>=0&&n<=255)&&![0,10,127].includes(p[0])&&p[0]<224&&
 !(p[0]===169&&p[1]===254)&&!(p[0]===172&&p[1]>=16&&p[1]<=31)&&!(p[0]===192&&p[1]===168)&&
 !(p[0]===100&&p[1]>=64&&p[1]<=127)&&!(p[0]===198&&[18,19].includes(p[1]))&&
 !(p[0]===192&&p[1]===0)&&!(p[0]===198&&p[1]===51&&p[2]===100)&&!(p[0]===203&&p[1]===0&&p[2]===113);
}
export function parseSource(raw,allowHttp){
 const u=new URL(raw);
 if(!['http:','https:'].includes(u.protocol)||u.username||u.password||u.hostname==='localhost'||u.href.length>8192)throw Error('Fuente no permitida.');
 if(u.protocol==='http:'&&!allowHttp)throw Error('Confirma la reproducción por HTTP antes de continuar.');
 return u;
}
function resource(session,url){
 parseSource(url,session.allowHttp);
 if(session.urls.has(url))return '/api/stream/'+session.urls.get(url);
 if(session.tokens.size>=2048){const oldest=session.tokens.keys().next().value;session.urls.delete(session.tokens.get(oldest));session.tokens.delete(oldest)}
 const token=crypto.randomUUID();session.tokens.set(token,url);session.urls.set(url,token);return '/api/stream/'+token;
}
export function rewriteManifest(text,base,session){
 return text.split(/\r?\n/).map(line=>{
  if(!line.trim())return line;
  if(line.startsWith('#'))return line.replace(/URI="([^"]+)"/g,(_,url)=>'URI="'+resource(session,new URL(url,base).href)+'"');
  return resource(session,new URL(line.trim(),base).href);
 }).join('\n');
}
async function upstream(raw,allowHttp,signal,range,redirects=0){
 const url=parseSource(raw,allowHttp);
 const addresses=await lookup(url.hostname,{family:4,all:true});
 if(!addresses.length||addresses.some(a=>!publicIPv4(a.address)))throw Error('La fuente debe estar en un servidor público.');
 const address=addresses[0].address;
 const response=await new Promise((resolve,reject)=>{
  const headers={'User-Agent':'InterFlashTV/1.0',Accept:'*/*'};
  if(range&&/^bytes=\d*-\d*$/.test(range))headers.Range=range;
  const request=(url.protocol==='https:'?httpsRequest:httpRequest)(url,{
   signal,headers,lookup:(_host,options,callback)=>options&&options.all?callback(null,[{address,family:4}]):callback(null,address,4)
  },resolve);
  request.setTimeout(20000,()=>request.destroy(Error('El proveedor no respondió a tiempo.')));
  request.on('error',reject);request.end();
 });
 if(response.statusCode>=300&&response.statusCode<400){
  response.resume();
  if(redirects>=4||!response.headers.location)throw Error('Redirección no válida.');
  return upstream(new URL(response.headers.location,url).href,allowHttp,signal,range,redirects+1);
 }
 if(![200,206].includes(response.statusCode)){response.resume();throw Error('El proveedor respondió con error '+response.statusCode+'.')}
 return {response,url};
}
export async function handleRelay(req){
 const url=new URL(req.url),path=url.pathname;
 if(path!=='/api/playback'&&!path.startsWith('/api/stream/'))return null;
 for(const [id,session] of sessions)if(session.expires<Date.now()){for(const c of session.controllers)c.abort();sessions.delete(id)}
 let id=(req.headers.get('cookie')||'').split(';').map(v=>v.trim()).find(v=>v.startsWith('if_tv_session='))?.slice(14);
 if(path==='/api/playback'){
  if(req.method!=='POST')return Response.json({error:'Método no permitido.'},{status:405,headers:noStore});
  if(req.headers.get('origin')!==APP_ORIGIN)return Response.json({error:'Origen no permitido.'},{status:403,headers:noStore});
  try{
   const raw=await req.text();if(raw.length>10000)throw Error('Solicitud demasiado grande.');
   const body=JSON.parse(raw),allowHttp=body.insecureConfirmed===true;
   const source=parseSource(body.url,allowHttp);
   // No network request occurs until the user has accepted HTTP in the player.
   let session=id&&sessions.get(id);
   if(!session){if(sessions.size>=200)return Response.json({error:'Reproductor ocupado. Inténtalo de nuevo.'},{status:429,headers:noStore});id=crypto.randomUUID();session={tokens:new Map(),urls:new Map(),controllers:new Set(),expires:0,allowHttp};sessions.set(id,session)}
   for(const c of session.controllers)c.abort();session.controllers.clear();session.tokens.clear();session.urls.clear();session.allowHttp=allowHttp;session.expires=Date.now()+6*60*60*1000;
   return Response.json({url:resource(session,source.href)},{headers:{...noStore,'Set-Cookie':'if_tv_session='+id+'; HttpOnly; Secure; SameSite=Strict; Path=/api/; Max-Age=21600'}});
  }catch(e){return Response.json({error:e.message==='Confirma la reproducción por HTTP antes de continuar.'?e.message:'Fuente no permitida.'},{status:400,headers:noStore})}
 }
 if(req.method!=='GET')return new Response(null,{status:405,headers:noStore});
 const session=id&&sessions.get(id),token=path.slice('/api/stream/'.length),source=session?.tokens.get(token);
 if(!source)return new Response('La sesión de reproducción ha caducado.',{status:403,headers:noStore});
 if(session.controllers.size>=8)return new Response('Demasiadas solicitudes.',{status:429,headers:noStore});
 const controller=new AbortController();session.controllers.add(controller);
 const abort=()=>controller.abort();req.signal.addEventListener('abort',abort,{once:true});
 const cleanup=()=>{session.controllers.delete(controller);req.signal.removeEventListener('abort',abort)};
 try{
  const {response,url:resolved}=await upstream(source,session.allowHttp,controller.signal,req.headers.get('range'));
  response.on('error',cleanup);response.on('close',cleanup);
  const type=String(response.headers['content-type']||'');
  if(/mpegurl/i.test(type)||/\.m3u8$/i.test(resolved.pathname)){
   let size=0;const chunks=[];for await(const chunk of response){size+=chunk.length;if(size>2*1024*1024){response.destroy();throw Error('Lista de reproducción demasiado grande.')}chunks.push(chunk)}
   const text=Buffer.concat(chunks).toString('utf8');if(!text.trimStart().startsWith('#EXTM3U'))throw Error('El proveedor no devolvió una señal válida.');
   cleanup();return new Response(rewriteManifest(text,resolved,session),{headers:{...noStore,'Content-Type':'application/vnd.apple.mpegurl'}});
  }
  const headers={...noStore,'Content-Type':/^(video|audio)\//i.test(type)?type:'video/mp2t'};
  for(const name of ['content-range','accept-ranges','content-length'])if(response.headers[name])headers[name]=String(response.headers[name]);
  return new Response(Readable.toWeb(response),{status:response.statusCode,headers});
 }catch(e){controller.abort();cleanup();return Response.json({error:'No se pudo conectar con la señal del proveedor.'},{status:502,headers:noStore})}
}

async function downloadM3U(raw,signal,redirects=0,allowHttp=raw.startsWith("http:")){const url=new URL(raw);if(url.protocol==="http:"&&!allowHttp)throw Error("El proveedor intenta redirigir a HTTP. No se ha enviado la clave por esa conexión.");if(!["http:","https:"].includes(url.protocol)||url.username||url.password||"localhost"===url.hostname)throw Error("Enlace no permitido.");const addresses=await lookup(url.hostname,{family:4,all:!0});if(!addresses.length||addresses.some(a=>!function(address){const p=address.split(".").map(Number);return 4===p.length&&p.every(n=>Number.isInteger(n)&&n>=0&&n<=255)&&![0,10,127].includes(p[0])&&!(169===p[0]&&254===p[1])&&!(172===p[0]&&p[1]>=16&&p[1]<=31)&&!(192===p[0]&&168===p[1])&&!(100===p[0]&&p[1]>=64&&p[1]<=127)&&!(198===p[0]&&[18,19].includes(p[1]))&&p[0]<224}(a.address)))throw Error("El enlace debe apuntar a un servidor público.");const address=addresses[0].address,result=await new Promise((resolve,reject)=>{const request=("https:"===url.protocol?httpsRequest:httpRequest)(url,{signal:signal,headers:{"User-Agent":"InterFlashTV/1.0",Accept:"application/x-mpegURL, audio/mpegurl, text/plain, */*"},lookup:(_host,options,callback)=>options&&options.all?callback(null,[{address:address,family:4}]):callback(null,address,4)},response=>{if(response.statusCode>=300&&response.statusCode<400)return response.resume(),void resolve({redirect:response.headers.location});if(200!==response.statusCode)return response.resume(),void reject(Error(401===response.statusCode||403===response.statusCode?"El proveedor rechazó el enlace. Revisa el usuario, la clave o la vigencia de la lista.":"El proveedor respondió con error "+response.statusCode+"."));const chunks=[];let size=0;response.on("data",chunk=>{size+=chunk.length,size>5e6?response.destroy(Error("La lista supera el límite de 5 MB.")):chunks.push(chunk)}),response.on("error",reject),response.on("end",()=>resolve({text:Buffer.concat(chunks).toString("utf8")}))});request.on("error",reject),request.end()});if(result.redirect){if(redirects>=3)throw Error("Demasiadas redirecciones.");return downloadM3U(new URL(result.redirect,url).href,signal,redirects+1,allowHttp)}return result.text}

const PAGE_URL='https://raw.githubusercontent.com/manuel829mg-cloud/InterFlashTV/37846bc710c89fe63477743fce0e1edab2849486/web/index.html';
let pagePromise;
function loadPage(){
 if(!pagePromise)pagePromise=fetch(PAGE_URL,{signal:AbortSignal.timeout(15000)}).then(async r=>{if(!r.ok)throw Error('Página no disponible');return r.text()}).catch(e=>{pagePromise=null;throw e});
 return pagePromise;
}

const playlistLinks=new Map(),playlistClaims=new Map();
async function handlePlaylistCode(req){
const path=new URL(req.url).pathname;if(!['/api/playlist-link','/api/playlist-claim'].includes(path))return null;
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer'};
if(req.method!=='POST'||req.headers.get('origin')!==APP_ORIGIN)return Response.json({error:'Solicitud no permitida.'},{status:403,headers});
for(const [key,item] of playlistLinks)if(item.expires<Date.now())playlistLinks.delete(key);
for(const [key,item] of playlistClaims)if(item.expires<Date.now())playlistClaims.delete(key);
const ip=req.headers.get('x-real-ip')||req.headers.get('x-forwarded-for')||'unknown';
const rate=playlistClaims.get(ip)||{attempts:0,expires:Date.now()+60000};rate.attempts++;playlistClaims.set(ip,rate);
if(rate.attempts>10)return Response.json({error:'Espera un minuto antes de volver a intentar.'},{status:429,headers});
try{
const raw=await req.text();if(raw.length>10000)throw Error();const body=JSON.parse(raw);
if(path==='/api/playlist-link'){
 const url=new URL(body.url);if(!['http:','https:'].includes(url.protocol)||url.username||url.password||url.href.length>8192)throw Error();
 if(playlistLinks.size>=100)return Response.json({error:'Inténtalo más tarde.'},{status:429,headers});
 const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';let code;do{code='IF-'+Array.from(crypto.getRandomValues(new Uint8Array(10)),n=>alphabet[n%alphabet.length]).join('')}while(playlistLinks.has(code));
 playlistLinks.set(code,{url:url.href,expires:Date.now()+10*60000});
 return Response.json({code,expiresIn:600},{headers});
}
const code=String(body.code||'').trim().toUpperCase(),item=playlistLinks.get(code);
if(!item)return Response.json({error:'Código incorrecto, usado o vencido. Solicita uno nuevo.'},{status:404,headers});
playlistLinks.delete(code);return Response.json({url:item.url},{headers});
}catch(e){return Response.json({error:'Datos no válidos.'},{status:400,headers})}
}


const tvPairs=new Map();
async function handleTVTransfer(req){
const path=new URL(req.url).pathname;if(!['/api/tv-link','/api/tv-send','/api/tv-receive'].includes(path))return null;
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer'};
if(req.method!=='POST'||req.headers.get('origin')!==APP_ORIGIN)return Response.json({error:'Solicitud no permitida.'},{status:403,headers});
for(const [key,item] of tvPairs)if(item.expires<Date.now())tvPairs.delete(key);
try{
if(Number(req.headers.get('content-length'))>5200000)throw Error('La lista supera 5 MB.');
const raw=await req.text();if(Buffer.byteLength(raw)>5200000)throw Error('La lista supera 5 MB.');const body=JSON.parse(raw);
if(path==='/api/tv-link'){
if(tvPairs.size>=10)throw Error('Ya hay varias vinculaciones pendientes. Espera unos minutos.');
const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';let code;do{code='TV-'+Array.from(crypto.getRandomValues(new Uint8Array(10)),n=>alphabet[n%alphabet.length]).join('')}while(tvPairs.has(code));
const pollToken=crypto.randomUUID()+crypto.randomUUID();tvPairs.set(code,{pollToken,text:null,name:'Lista desde PC',expires:Date.now()+600000});return Response.json({code,pollToken},{headers});
}
if(path==='/api/tv-send'){
const code=String(body.code||'').trim().toUpperCase(),item=tvPairs.get(code);if(!item)throw Error('Código incorrecto o vencido. Abre Recibir desde PC en el televisor.');if(item.text)throw Error('La lista ya fue enviada a ese televisor.');
if(typeof body.text!=='string'||body.text.length>5000000||!/^\s*#EXTM3U(?:\s|$)/i.test(body.text)||!/#EXTINF:[\s\S]*?\r?\nhttps?:[/][/]/i.test(body.text))throw Error('La PC no tiene una lista M3U válida cargada.');
item.text=body.text;item.name=String(body.name||'Lista desde PC').slice(0,120);return Response.json({sent:true},{headers});
}
const found=Array.from(tvPairs.entries()).find(([code,item])=>item.pollToken===body.pollToken);
if(!found)return Response.json({error:'La vinculación venció. Abre Recibir desde PC otra vez.'},{status:404,headers});
const [code,item]=found;if(!item.text)return Response.json({pending:true},{status:202,headers});
tvPairs.delete(code);return Response.json({text:item.text,name:item.name},{headers});
}catch(e){return Response.json({error:e instanceof SyntaxError?'Datos no válidos.':e.message},{status:400,headers})}
}

Bun.serve({port:Number(Bun.env.PORT||3000),async fetch(req){
 const transfer=await handleTVTransfer(req);if(transfer)return transfer;
 const codeResponse=await handlePlaylistCode(req);if(codeResponse)return codeResponse;
 const relay=await handleRelay(req);if(relay)return relay;
 const path=new URL(req.url).pathname;
 if(path==='/api/m3u-import'){
  const headers={'Cache-Control':'no-store'};
  if(req.method!=='POST')return Response.json({error:'Método no permitido.'},{status:405,headers});
  if(req.headers.get('origin')&&req.headers.get('origin')!==APP_ORIGIN)return Response.json({error:'Origen no permitido.'},{status:403,headers});
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),18000);
  try{
   const body=await req.text();if(body.length>8192)throw Error('El enlace es demasiado largo.');
   const {url}=JSON.parse(body);if(typeof url!=='string'||!url.trim())throw Error('Introduce el enlace de la lista.');
   const text=await downloadM3U(url,controller.signal);
   const lines=text.replace(/^\uFEFF/,'').trim().split(/\r?\n/).map(line=>line.trim());let pending=false,valid=false;for(const line of lines){if(line.startsWith('#EXTINF:'))pending=true;else if(pending&&/^https?:[/][/]/i.test(line)){try{if(!/\/(get|player_api)\.php$/i.test(new URL(line).pathname))valid=true}catch(e){}pending=false}}if(!/^#EXTM3U(?:\s|$)/i.test(lines[0]||'')||!valid)throw Error('El proveedor no devolvió una lista de canales M3U válida.');
   return new Response(text,{headers:{...headers,'Content-Type':'text/plain; charset=utf-8'}});
  }catch(e){return Response.json({error:e.name==='AbortError'?'El proveedor tardó demasiado en responder.':/^(El proveedor|La lista|El enlace|Introduce)/.test(e.message)?e.message:'No se pudo conectar por HTTPS con el proveedor.'},{status:400,headers})}
  finally{clearTimeout(timer)}
 }
 if(path==='/digital15-logo.png'){const r=await fetch('https://i.imgur.com/v3mkmZa.png');return new Response(r.body,{status:r.status,headers:{'content-type':'image/png'}})}
 try{return new Response(await loadPage(),{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-cache','Referrer-Policy':'no-referrer'}})}
 catch(e){return new Response('La aplicación se está iniciando. Recarga en unos segundos.',{status:503,headers:{'Content-Type':'text/plain; charset=utf-8'}})}
}});
