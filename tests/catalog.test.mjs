import assert from 'node:assert/strict';
import {test} from 'node:test';
import {handleCatalogue,sessions} from '../web/runtime.ts';
globalThis.Bun={env:{TV_DEFAULT_PLAYLIST_URL:'https://provider.example/get.php?username=private-user&password=private-password'}};
const origin='https://inter-tv-prueba-production.up.railway.app';
function request(body,{cookie='test-session',source=origin}={}){return new Request(origin+'/api/catalog',{method:'POST',headers:{origin:source,cookie:'if_tv_session='+cookie,'content-type':'application/json'},body:JSON.stringify(body)})}
function session(){const s={defaultApproved:true,expires:Date.now()+60000,allowHttp:false,tokens:new Map(),urls:new Map(),maxResources:25000};sessions.set('test-session',s);return s}
test('rejects missing, expired, and foreign sessions before contacting provider',async()=>{
 let calls=0;const read=async()=>{calls++;return []};session();
 for(const req of [request({kind:'movies',view:'items'},{cookie:'none'}),request({kind:'movies',view:'items'},{source:'https://other.example'})])assert.ok((await handleCatalogue(req,read)).status>=400);
 sessions.get('test-session').expires=0;assert.equal((await handleCatalogue(request({kind:'movies',view:'items'}),read)).status,401);assert.equal(calls,0);
});
test('pages and searches movies without returning credentials or raw stream URLs',async()=>{
 session();const rows=Array.from({length:55},(_,i)=>({stream_id:i+1,name:'Película '+i,category_id:'8',stream_icon:'https://images.example/'+i+'.jpg'}));
 const r=await handleCatalogue(request({kind:'movies',view:'items',category:'8'}),async(action,params)=>{assert.equal(action,'get_vod_streams');assert.equal(params.category_id,'8');return rows});
 const body=await r.json();assert.equal(body.items.length,48);assert.equal(body.total,55);assert.equal(body.hasMore,true);assert.ok(body.items[0].art.startsWith('/api/stream/'));assert.ok(!JSON.stringify(body).includes('private-'));
 const last=await(await handleCatalogue(request({kind:'movies',view:'items',page:1}),async()=>rows)).json();assert.equal(last.items.length,7);assert.equal(last.hasMore,false);
 const found=await(await handleCatalogue(request({kind:'movies',view:'items',q:'película 54'}),async()=>rows)).json();assert.equal(found.total,1);assert.equal(found.items[0].id,'55');
});
test('selects correct series API and orders episodes by season and episode',async()=>{
 session();const categories=await(await handleCatalogue(request({kind:'series',view:'categories'}),async action=>{assert.equal(action,'get_series_categories');return [{category_id:'9',category_name:'Drama'}]})).json();assert.equal(categories.categories[0].name,'Drama');
 const r=await handleCatalogue(request({kind:'series',view:'episodes',id:'17'}),async(action,params)=>{assert.equal(action,'get_series_info');assert.equal(params.series_id,'17');return {info:{plot:'Trama'},episodes:{2:[{id:22,episode_num:1,title:'Segundo'}],1:[{id:12,episode_num:2,title:'Dos'},{id:11,episode_num:1,title:'Uno'}]}}});
 const data=await r.json();assert.deepEqual(data.episodes.map(x=>x.id),['11','12','22']);assert.equal(data.plot,'Trama');
});
test('playback uses opaque tokens and preserves live session tokens',async()=>{
 const s=session();s.tokens.set('live-token','https://provider.example/live');
 const r=await handleCatalogue(request({kind:'movies',view:'play',id:'123',extension:'mkv'}),async()=>{throw Error('No listing request needed')});const data=await r.json();
 assert.match(data.url,/\/api\/stream\/[^?]+\?format=file$/);assert.ok(!data.url.includes('private-'));assert.ok(s.tokens.has('live-token'));assert.ok([...s.catalogueMedia.tokens.values()].some(x=>x.endsWith('/movie/private-user/private-password/123.mkv')));
});
test('rejects invalid kinds, IDs, categories and provider errors',async()=>{
 session();let calls=0;const read=async()=>{calls++;throw Error('secret-password')};
 for(const body of [{kind:'admin',view:'items'},{kind:'movies',view:'play',id:'../../secrets'},{kind:'movies',view:'items',category:'../../'}])assert.equal((await handleCatalogue(request(body),read)).status,400);
 assert.equal(calls,0);const r=await handleCatalogue(request({kind:'movies',view:'items'}),read);assert.equal(r.status,502);assert.ok(!(await r.text()).includes('secret-password'));
});

test('catalogue artwork does not evict saturated live channel tokens',async()=>{const s=session();s.maxResources=1;s.tokens.set('only-live','https://provider.example/live');s.urls.set('https://provider.example/live','only-live');await handleCatalogue(request({kind:'movies',view:'items'}),async()=>[{stream_id:1,name:'Una película',stream_icon:'https://images.example/cover.jpg'}]);assert.equal(s.tokens.get('only-live'),'https://provider.example/live');assert.equal(s.catalogueArtwork.tokens.size,1)});
