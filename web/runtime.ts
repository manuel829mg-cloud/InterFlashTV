import {lookup} from 'node:dns/promises';
import {request as httpRequest} from 'node:http';
import {request as httpsRequest} from 'node:https';
import {Readable} from 'node:stream';

const APP_ORIGIN='https://inter-tv-prueba-production.up.railway.app';
export const sessions=new Map();
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
 if(session.tokens.size>=(session.maxResources||2048)){const oldest=session.tokens.keys().next().value;session.urls.delete(session.tokens.get(oldest));session.tokens.delete(oldest)}
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
 const session=id&&sessions.get(id),token=path.slice('/api/stream/'.length),source=session?.tokens.get(token)||session?.catalogueMedia?.tokens.get(token)||session?.catalogueArtwork?.tokens.get(token);
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
  const headers={...noStore,'Content-Type':/^(video|audio|image)\//i.test(type)?type:'video/mp2t'};
  for(const name of ['content-range','accept-ranges','content-length'])if(response.headers[name])headers[name]=String(response.headers[name]);
  return new Response(Readable.toWeb(response),{status:response.statusCode,headers});
 }catch(e){controller.abort();cleanup();return Response.json({error:'No se pudo conectar con la señal del proveedor.'},{status:502,headers:noStore})}
}

async function downloadM3U(raw,signal,redirects=0,allowHttp=raw.startsWith("http:"),byteLimit=5e6){const url=new URL(raw);if(url.protocol==="http:"&&!allowHttp)throw Error("El proveedor intenta redirigir a HTTP. No se ha enviado la clave por esa conexión.");if(!["http:","https:"].includes(url.protocol)||url.username||url.password||"localhost"===url.hostname)throw Error("Enlace no permitido.");const addresses=await lookup(url.hostname,{family:4,all:!0});if(!addresses.length||addresses.some(a=>!function(address){const p=address.split(".").map(Number);return 4===p.length&&p.every(n=>Number.isInteger(n)&&n>=0&&n<=255)&&![0,10,127].includes(p[0])&&!(169===p[0]&&254===p[1])&&!(172===p[0]&&p[1]>=16&&p[1]<=31)&&!(192===p[0]&&168===p[1])&&!(100===p[0]&&p[1]>=64&&p[1]<=127)&&!(198===p[0]&&[18,19].includes(p[1]))&&p[0]<224}(a.address)))throw Error("El enlace debe apuntar a un servidor público.");const address=addresses[0].address,result=await new Promise((resolve,reject)=>{const request=("https:"===url.protocol?httpsRequest:httpRequest)(url,{signal:signal,headers:{"User-Agent":"InterFlashTV/1.0",Accept:"application/x-mpegURL, audio/mpegurl, text/plain, */*"},lookup:(_host,options,callback)=>options&&options.all?callback(null,[{address:address,family:4}]):callback(null,address,4)},response=>{if(response.statusCode>=300&&response.statusCode<400)return response.resume(),void resolve({redirect:response.headers.location});if(200!==response.statusCode)return response.resume(),void reject(Error(401===response.statusCode||403===response.statusCode?"El proveedor rechazó el enlace. Revisa el usuario, la clave o la vigencia de la lista.":"El proveedor respondió con error "+response.statusCode+"."));const chunks=[];let size=0;response.on("data",chunk=>{size+=chunk.length,size>byteLimit?response.destroy(Error("La lista supera el límite de 5 MB.")):chunks.push(chunk)}),response.on("error",reject),response.on("end",()=>resolve({text:Buffer.concat(chunks).toString("utf8")}))});request.on("error",reject),request.end()});if(result.redirect){if(redirects>=3)throw Error("Demasiadas redirecciones.");return downloadM3U(new URL(result.redirect,url).href,signal,redirects+1,allowHttp,byteLimit)}return result.text}

const PAGE_URL='https://raw.githubusercontent.com/manuel829mg-cloud/InterFlashTV/1c728ac3a216fcb15854cc4fa9008c06e34b43bd/web/index.html';
let pagePromise;
function loadPage(){
 if(!pagePromise)pagePromise=fetch(PAGE_URL,{signal:AbortSignal.timeout(15000)}).then(async r=>{if(!r.ok)throw Error('Página no disponible');return r.text()}).catch(e=>{pagePromise=null;throw e});
 return pagePromise;
}


let defaultCache=null;
function defaultSession(req){const id=(req.headers.get('cookie')||'').split(';').map(v=>v.trim()).find(v=>v.startsWith('if_tv_session='))?.slice(14);return id?sessions.get(id):null}
async function handleDefaultPlaylist(req){
if(new URL(req.url).pathname!=='/api/default-playlist')return null;
const headers={'Cache-Control':'no-store'},source=Bun.env.TV_DEFAULT_PLAYLIST_URL;
if(!source)return Response.json({configured:false},{headers});
const sourceUrl=new URL(source),prior=defaultSession(req);
if(req.method==='GET')return Response.json({configured:true,host:sourceUrl.host,requiresHttp:sourceUrl.protocol==='http:',approved:!!(prior?.defaultApproved&&prior.expires>Date.now())},{headers});
if(req.method!=='POST'||req.headers.get('origin')!==APP_ORIGIN)return Response.json({error:'Solicitud no permitida.'},{status:403,headers});
try{
const body=await req.json(),confirmed=body.insecureConfirmed===true||!!(prior?.defaultApproved&&prior.expires>Date.now());
if(sourceUrl.protocol==='http:'&&!confirmed)return Response.json({error:'Confirma la conexión HTTP del proveedor.'},{status:428,headers});
if(!defaultCache||defaultCache.expires<Date.now()){
const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),25000);
try{const text=await downloadM3U(source,controller.signal);if(!/^\s*#EXTM3U(?:\s|$)/i.test(text))throw Error('El proveedor no devolvió una lista M3U válida.');defaultCache={text,expires:Date.now()+600000}}finally{clearTimeout(timer)}
}
if(!prior&&Array.from(sessions.values()).filter(s=>s.defaultApproved&&s.expires>Date.now()).length>=10)throw Error('Hay demasiados dispositivos conectados.');
const session=prior||{tokens:new Map(),urls:new Map(),controllers:new Set()};for(const c of session.controllers)c.abort();session.controllers.clear();session.tokens.clear();session.urls.clear();session.allowHttp=confirmed;session.defaultApproved=true;session.maxResources=25000;session.expires=Date.now()+21600000;
const id=crypto.randomUUID();if(prior){for(const [key,value] of sessions)if(value===prior)sessions.delete(key)}sessions.set(id,session);
const output=['#EXTM3U'];let name='',group='General',count=0;
function clean(value){return value.replace(/[\r\n"]/g,' ').slice(0,180)}
for(let line of defaultCache.text.split(/\r?\n/)){line=line.trim();if(line.startsWith('#EXTINF:')){name=(line.split(',').slice(1).join(',')||'Canal').trim();group=(line.match(/group-title="([^"]*)"/)||[])[1]||'General'}else if(/^https?:[/][/]/i.test(line)&&name){
const parsed=parseSource(line,confirmed),format=/\.m3u8$/i.test(parsed.pathname)?'m3u8':/\.(mp4|m4v|webm|mp3|aac|m4a)$/i.test(parsed.pathname)?'file':'ts';
output.push('#EXTINF:-1 group-title="'+clean(group)+'",'+clean(name));output.push(APP_ORIGIN+resource(session,parsed.href)+'?format='+format);name='';group='General';count++}}
if(!count){sessions.delete(id);throw Error('El proveedor no devolvió canales válidos.')}
return new Response(output.join('\n'),{headers:{...headers,'Content-Type':'application/x-mpegURL; charset=utf-8','Set-Cookie':'if_tv_session='+id+'; HttpOnly; Secure; SameSite=Strict; Path=/api/; Max-Age=21600'}});
}catch(e){return Response.json({error:e.name==='AbortError'?'El proveedor tardó demasiado. No se cambió tu lista.':/^(El proveedor|Hay demasiados)/.test(e.message)?e.message:'No se pudo descargar la lista del proveedor.'},{status:502,headers})}
}

// Catalogues use the configured provider; provider credentials stay on the server.
const catalogueCache=new Map();
function providerConfig(){
 const raw=Bun.env.TV_DEFAULT_PLAYLIST_URL;if(!raw)throw Error('No hay una lista configurada.');
 const u=new URL(raw),username=u.searchParams.get('username'),password=u.searchParams.get('password');
 if(!username||!password)throw Error('Esta lista no incluye acceso al catálogo de películas y series.');
 return {base:u.origin,username,password};
}
async function catalogueProviderRead(action,params={}){
 const config=providerConfig(),u=new URL('/player_api.php',config.base);
 u.searchParams.set('username',config.username);u.searchParams.set('password',config.password);u.searchParams.set('action',action);
 for(const [k,v]of Object.entries(params))if(v)u.searchParams.set(k,String(v));
 const key=action+JSON.stringify(params);let entry=catalogueCache.get(key);
 if(entry&&entry.expires>Date.now())return entry.data;
 const raw=await downloadM3U(u.href,AbortSignal.timeout(20000),0,u.protocol==='http:',20000000);
 const data=JSON.parse(raw);if(!data||typeof data!=='object')throw Error('Catálogo no disponible.');
 if(catalogueCache.size>=8)catalogueCache.delete(catalogueCache.keys().next().value);
 catalogueCache.set(key,{data,expires:Date.now()+300000});return data;
}
function catalogueResource(session,url,art=false){
 const key=art?'catalogueArtwork':'catalogueMedia';
 if(!session[key])session[key]={allowHttp:session.allowHttp,tokens:new Map(),urls:new Map(),maxResources:art?2048:128};
 return resource(session[key],url);
}
function catalogueArt(raw,session){
 try{const u=new URL(String(raw));if(!['http:','https:'].includes(u.protocol)||u.username||u.password)return '';return catalogueResource(session,u.href,true)}catch{return ''}
}
export function catalogueItems(rows,kind,session){
 if(!Array.isArray(rows))throw Error('El proveedor no devolvió un catálogo válido.');
 return rows.filter(r=>r&&/^\d+$/.test(String(kind==='series'?r.series_id:r.stream_id))).map(r=>({
 id:String(kind==='series'?r.series_id:r.stream_id),name:String(r.name||'Sin título').slice(0,250),category:String(r.category_id||''),
 art:session?catalogueArt(r.cover||r.stream_icon,session):'',rating:String(r.rating||r.rating_5based||'').slice(0,12),
 year:String(r.year||r.releaseDate||r.release_date||'').slice(0,4),extension:/^[a-z0-9]{2,5}$/i.test(r.container_extension||'')?r.container_extension:'mp4'
 }));
}
export async function handleCatalogue(req,readProvider=catalogueProviderRead){
 const path=new URL(req.url).pathname;if(path!=='/api/catalog')return null;
 const headers={...noStore,'Content-Type':'application/json; charset=utf-8'};
 if(req.method!=='POST'||req.headers.get('origin')!==APP_ORIGIN)return Response.json({error:'Solicitud no permitida.'},{status:403,headers});
 const session=defaultSession(req);
 if(!session?.defaultApproved||session.expires<Date.now())return Response.json({error:'Primero conecta la lista desde TV en vivo.',code:'CONNECT_REQUIRED'},{status:401,headers});
 try{
 const body=await req.text();if(body.length>4096)return Response.json({error:'Solicitud demasiado grande.'},{status:413,headers});
 const input=JSON.parse(body),kind=input.kind;if(!['movies','series'].includes(kind))return Response.json({error:'Sección no válida.'},{status:400,headers});
 const series=kind==='series';
 if(input.view==='categories'){
 const rows=await readProvider(series?'get_series_categories':'get_vod_categories');if(!Array.isArray(rows))throw Error();
 return Response.json({categories:rows.map(r=>({id:String(r.category_id||''),name:String(r.category_name||'General').slice(0,180)}))},{headers});
 }
 if(input.view==='play'){
 if(!/^\d{1,15}$/.test(String(input.id)))return Response.json({error:'Título no válido.'},{status:400,headers});
 const ext=/^(mp4|mkv|avi|m4v|webm|ts|m3u8)$/i.test(input.extension||'')?input.extension:'mp4';
 const c=providerConfig(),u=new URL('/'+(series?'series':'movie')+'/'+encodeURIComponent(c.username)+'/'+encodeURIComponent(c.password)+'/'+input.id+'.'+ext,c.base);
 const format=ext==='m3u8'?'m3u8':ext==='ts'?'ts':'file';
 return Response.json({url:APP_ORIGIN+catalogueResource(session,u.href)+'?format='+format},{headers});
 }
 if(input.view==='episodes'&&series){
 if(!/^\d{1,15}$/.test(String(input.id)))return Response.json({error:'Serie no válida.'},{status:400,headers});
 const data=await readProvider('get_series_info',{series_id:String(input.id)}),episodes=[];
 for(const [season,rows]of Object.entries(data.episodes||{}))if(Array.isArray(rows))for(const e of rows)if(e&&/^\d+$/.test(String(e.id)))episodes.push({id:String(e.id),name:String(e.title||'Episodio '+(e.episode_num||'')).slice(0,250),season:String(e.season??season),number:Number(e.episode_num)||0,extension:/^[a-z0-9]{2,5}$/i.test(e.container_extension||'')?e.container_extension:'mp4'});
 episodes.sort((a,b)=>(Number(a.season)-Number(b.season))||(a.number-b.number));
 return Response.json({plot:String(data.info?.plot||'').slice(0,3000),episodes},{headers});
 }
 if(input.view!=='items')return Response.json({error:'Vista no válida.'},{status:400,headers});
 if(input.category&&!/^\d{1,15}$/.test(String(input.category)))return Response.json({error:'Categoría no válida.'},{status:400,headers});
 const data=await readProvider(series?'get_series':'get_vod_streams',input.category?{category_id:String(input.category)}:{});
 const query=String(input.q||'').toLocaleLowerCase('es').trim().slice(0,120);
 // Filter/page before generating image tokens to keep session memory bounded.
 let rows=catalogueItems(data,kind,null);if(query)rows=rows.filter(r=>r.name.toLocaleLowerCase('es').includes(query));
 const page=Math.max(0,Math.min(5000,Number.isFinite(Number(input.page))?Math.floor(Number(input.page)):0)),start=page*48;
 const original=new Map(data.map(r=>[String(series?r.series_id:r.stream_id),r]));
 const items=rows.slice(start,start+48).map(r=>({...r,art:catalogueArt(original.get(r.id)?.cover||original.get(r.id)?.stream_icon,session)}));
 return Response.json({items,total:rows.length,page,hasMore:start+48<rows.length},{headers});
 }catch{return Response.json({error:'No se pudo cargar el catálogo. Revisa la conexión o la disponibilidad con tu proveedor.'},{status:502,headers})}
}

if(import.meta.main)Bun.serve({idleTimeout:60,port:Number(Bun.env.PORT||3000),async fetch(req){
 const catalogue=await handleCatalogue(req);if(catalogue)return catalogue;
 const defaults=await handleDefaultPlaylist(req);if(defaults)return defaults;
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

