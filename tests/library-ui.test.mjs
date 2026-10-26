import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
const {JSDOM,VirtualConsole}=createRequire(new URL('../web/package.json',import.meta.url))('jsdom');
const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
const tick=()=>new Promise(r=>setTimeout(r,20));
async function app(){
 const calls=[],errors=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>errors.push(e));
 const dom=new JSDOM(html,{url:'https://inter-tv-prueba-production.up.railway.app/',runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,beforeParse(w){
 w.scrollTo=()=>{};w.HTMLElement.prototype.scrollIntoView=()=>{};w.HTMLMediaElement.prototype.pause=()=>{};w.HTMLMediaElement.prototype.load=()=>{};w.HTMLMediaElement.prototype.play=async()=>{};
 w.fetch=async(url,options={})=>{if(String(url).includes('/api/catalog')){
 const body=JSON.parse(options.body);calls.push(body);let value={};
 if(body.view==='categories')value={categories:[{id:'8',name:'Drama'}]};
 if(body.view==='items')value={items:[{id:'17',name:body.kind==='series'?'Mi serie':'Película <img src=x>',art:'',year:'2026',extension:'mp4'}],total:1,page:0,hasMore:false};
 if(body.view==='episodes'){await new Promise(r=>setTimeout(r,40));value={plot:'Historia de prueba',episodes:[{id:'44',season:'1',number:1,name:'Primer episodio',extension:'mp4'}]}}
 if(body.view==='play')value={url:'https://inter-tv-prueba-production.up.railway.app/api/stream/mock?format=file'};
 return Response.json(value)}
 if(String(url).includes('/api/default-playlist'))return Response.json({configured:false});
 return new Response('#EXTM3U\n#EXTINF:-1 group-title="General",Canal de prueba\nhttps://example.com/live.m3u8');
 };
 }});await tick();dom.window.play=async raw=>{calls.push({play:raw})};return {dom,w:dom.window,d:dom.window.document,calls,errors};
}
test('home navigation opens movies, searches, and requests selected playback safely',async()=>{
 const {dom,w,d,calls,errors}=await app();try{
 assert.equal(d.querySelector('#libraryHome').hidden,false);assert.equal(w.getComputedStyle(d.querySelector('.tv-grid')).display,'none');assert.equal(d.querySelectorAll('.reference-nav button[data-screen]').length,4);
 w.interflashNavigate('movies');await tick();assert.equal(d.querySelector('#mediaLibrary').hidden,false);assert.equal(d.querySelector('.tv-grid').hidden,true);
 const card=d.querySelector('.media-card');assert.ok(card);assert.match(card.textContent,/Película <img src=x>/);assert.equal(card.querySelectorAll('img').length,0);
 card.click();d.querySelector('.media-info .library-button').click();await tick();assert.ok(calls.some(c=>c.view==='play'&&c.id==='17'));assert.equal(d.querySelector('#player').controls,true);
 const search=d.querySelector('.library-toolbar input');search.value='acción';search.dispatchEvent(new w.Event('input'));await new Promise(r=>setTimeout(r,350));assert.ok(calls.some(c=>c.view==='items'&&c.q==='acción'));
 w.interflashNavigate('tv');assert.equal(d.querySelector('.tv-grid').hidden,false);assert.ok(d.querySelector('.tv-grid .tv-player'));assert.equal(d.querySelector('#player').controls,false);
 assert.deepEqual(errors.map(e=>e.message),[]);
 }finally{dom.window.close()}
});
test('series displays episodes and ignores stale results after changing section',async()=>{
 const {dom,w,d,calls}=await app();try{
 w.interflashNavigate('series');await tick();d.querySelector('.media-card').click();await new Promise(r=>setTimeout(r,60));assert.match(d.querySelector('.episode-list').textContent,/Temporada 1.*Primer episodio/);
 d.querySelector('.episode-list button').click();await tick();assert.ok(calls.some(c=>c.view==='play'&&c.kind==='series'&&c.id==='44'));
 w.interflashNavigate('series');await tick();d.querySelector('.media-card').click();w.interflashNavigate('movies');await new Promise(r=>setTimeout(r,70));assert.equal(d.querySelector('.episode-list').children.length,0);assert.equal(d.querySelector('.library-heading h2').textContent,'Películas');
 }finally{dom.window.close()}
});
