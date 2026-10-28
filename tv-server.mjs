import http from 'node:http';
import { readFile } from 'node:fs/promises';
const port = Number(process.env.PORT || 3000);
const html = await readFile(new URL('./tv-web/index.html',import.meta.url));
http.createServer((req,res)=>{
  const path = new URL(req.url||'/', 'http://localhost').pathname;
  if(path==='/health') {res.writeHead(200,{'Content-Type':'text/plain'});return res.end('ok');}
  if(path!=='/' && path!=='/index.html') {res.writeHead(404);return res.end('Not found');}
  res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store',
    'Content-Security-Policy':"default-src 'self' https: data: blob:; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline'; connect-src 'self' https:; media-src https: blob: data:; img-src 'self' https: data:;",
    'X-Content-Type-Options':'nosniff'});
  res.end(html);
}).listen(port,'0.0.0.0',()=>console.log('Inter Flash TV web listening',port));
