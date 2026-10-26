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

const PAGE_URL='https://raw.githubusercontent.com/manuel829mg-cloud/InterFlashTV/0c1cbaec6f9a4bb0fc4e6c9a25a0801bc0b67694/web/index.html';
let pagePromise;
function loadPage(){
 if(!pagePromise)pagePromise=fetch(PAGE_URL,{signal:AbortSignal.timeout(15000)}).then(async r=>{if(!r.ok)throw Error('Página no disponible');return r.text()}).catch(e=>{pagePromise=null;throw e});
 return pagePromise;
}
Bun.serve({port:Number(Bun.env.PORT||3000),async fetch(req){
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
   if(!text.split(String.fromCharCode(10)).some(line=>/^https?:[/][/]/i.test(line.trim())))throw Error('El proveedor no devolvió una lista de canales M3U válida.');
   return new Response(text,{headers:{...headers,'Content-Type':'text/plain; charset=utf-8'}});
  }catch(e){return Response.json({error:e.name==='AbortError'?'El proveedor tardó demasiado en responder.':/^(El proveedor|La lista|El enlace|Introduce)/.test(e.message)?e.message:'No se pudo conectar por HTTPS con el proveedor.'},{status:400,headers})}
  finally{clearTimeout(timer)}
 }
 if(path==='/digital15-logo.png'){const r=await fetch('https://i.imgur.com/v3mkmZa.png');return new Response(r.body,{status:r.status,headers:{'content-type':'image/png'}})}
 try{return new Response(await loadPage(),{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-cache','Referrer-Policy':'no-referrer'}})}
 catch(e){return new Response('La aplicación se está iniciando. Recarga en unos segundos.',{status:503,headers:{'Content-Type':'text/plain; charset=utf-8'}})}
}});
