package com.interflash.tv;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
 private WebView web;
 private AlertDialog exitDialog;
 private boolean checkingBack = false;
 private FrameLayout root;
 private View fullscreenView;
 private WebChromeClient.CustomViewCallback fullscreenCallback;
 private static final String PANEL_ROOT = "https://interflash-isp-manager.up.railway.app";
 private final Handler panelHandler = new Handler(Looper.getMainLooper());
 private final ExecutorService panelExecutor = Executors.newSingleThreadExecutor();
 private final AtomicBoolean panelBusy = new AtomicBoolean(false);
 private String panelToken;
 private AlertDialog linkDialog;
 private TextView linkDialogText;
 private boolean linkDialogDismissed = false;
 private boolean pageReady = false;
 private int panelRevision = -1;
 private final Runnable panelPoll = new Runnable() {
  @Override public void run() { syncPanel(); panelHandler.postDelayed(this, 8000); }
 };
 private final WebChromeClient chrome = new WebChromeClient() {
  @Override public void onShowCustomView(View view, CustomViewCallback callback) {
   if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
   fullscreenView = view;
   fullscreenCallback = callback;
   web.setVisibility(View.GONE);
   root.addView(view, new FrameLayout.LayoutParams(-1, -1));
   getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
  }
  @Override public void onHideCustomView() { exitFullscreen(); }
 };
 private void exitFullscreen() {
  if (fullscreenView == null) return;
  root.removeView(fullscreenView);
  fullscreenView = null;
  web.setVisibility(View.VISIBLE);
  if (fullscreenCallback != null) { fullscreenCallback.onCustomViewHidden(); fullscreenCallback = null; }
 }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(5894);
  root = new FrameLayout(this);
  web = new WebView(this);
  web.setWebViewClient(new WebViewClient() {
   @Override public void onPageFinished(WebView view, String url) {
    view.evaluateJavascript("(function(){if(document.getElementById('interflash-tv-fit'))return;var s=document.createElement('style');s.id='interflash-tv-fit';s.textContent='video:fullscreen, video:-webkit-full-screen {width:100vw!important;height:100vh!important;object-fit:cover!important;}video::-webkit-media-controls,video::-webkit-media-controls-enclosure,video::-webkit-media-controls-overlay-play-button,video::-webkit-media-controls-start-playback-button,video::-internal-media-controls-overlay-cast-button{display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important}#m3uImport,#m3ufile,.tv-channels>p.muted:not(#count){display:none!important}.tv-channels{padding:10px!important}.tv-channels>h2{font-size:20px!important;margin:0 0 8px!important}#searchChannel{box-sizing:border-box;width:100%!important;min-width:0!important;padding:8px!important;margin:0 0 6px!important;font-size:16px!important}#count{font-size:12px!important;margin:3px 0 6px!important}#channelList{gap:2px!important}#channelList button{box-sizing:border-box;min-height:36px!important;padding:6px 9px!important;margin:1px 0!important;font-size:16px!important;line-height:1.25!important;border-radius:6px!important;width:100%!important}#channelList button img{width:28px!important;height:24px!important;margin-right:6px!important}';document.head.appendChild(s);var heading=document.querySelector(\'.tv-channels>h2\');if(heading)heading.textContent=\'Canales\';})();", null);
    view.evaluateJavascript("(function(){function clean(v){if(!v||v.tagName!=='VIDEO')return;v.controls=false;v.removeAttribute('controls');v.setAttribute('playsinline','');v.setAttribute('controlslist','nodownload noremoteplayback noplaybackrate')}document.querySelectorAll('video').forEach(clean);new MutationObserver(function(ms){ms.forEach(function(m){if(m.type==='attributes')clean(m.target);m.addedNodes.forEach(function(n){if(n.nodeType===1){clean(n);if(n.querySelectorAll)n.querySelectorAll('video').forEach(clean)}})})}).observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['controls']});document.addEventListener('click',function(e){var v=e.target.closest&&e.target.closest('video');if(v&&(document.fullscreenElement||v.classList.contains('tv-fullscreen'))){e.preventDefault();e.stopImmediatePropagation()}},true)})();", null);
    view.evaluateJavascript("(function(){\nif(window.__interflashNativeDominicano)return;\nwindow.__interflashNativeDominicano=true;\nvar category=null,custom=false,token='__if_dominicano__';\nfunction normalize(s){return String(s||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim()}\nfunction isDominican(name){\n var n=normalize(name);\n return n.indexOf('digital 15')>=0||n.indexOf('teleantilla')>=0||/\\bcanal 5\\b/.test(n)||/\\bcanal 11\\b/.test(n)||/\\btelesistema\\b/.test(n)\n}\nfunction syncOption(){\n category=document.getElementById('category');\n if(!category)return;\n if(!Array.from(category.options).some(function(o){return o.value===token})){\n  category.add(new Option('Dominicano',token));\n  document.dispatchEvent(new Event('interflash-categories'));\n }\n}\nfunction update(){\n syncOption();\n var remote=document.getElementById('categoryRemote');\n if(custom){\n  var list=document.getElementById('channelList'),count=0;\n  if(list)Array.from(list.querySelectorAll('button')).forEach(function(b){\n   var show=isDominican(b.dataset.channelName||b.textContent);\n   b.hidden=!show;\n   if(show){b.style.display='block';count++}else b.style.display='none';\n  });\n  var status=document.getElementById('count');\n  if(status)status.textContent=count+' canales dominicanos';\n  if(remote)remote.textContent='Dominicano';\n  var menu=document.getElementById('categoryRemoteMenu');\n  if(menu)Array.from(menu.querySelectorAll('button')).forEach(function(b){\n   if(b.textContent.trim()==='Dominicano'){b.dataset.selected='true';b.setAttribute('aria-current','true')}\n   else if(b.dataset.category===''){b.removeAttribute('data-selected');b.setAttribute('aria-current','false')}\n  });\n }\n}\nfunction install(){\n syncOption();\n if(!category||category.dataset.ifDominicanoHandler)return;\n category.dataset.ifDominicanoHandler='1';\n category.addEventListener('change',function(ev){\n  if(category.value===token){\n   ev.preventDefault();ev.stopImmediatePropagation();custom=true;category.value='';\n   category.dispatchEvent(new Event('change',{bubbles:true}));\n   requestAnimationFrame(update);\n  }else if(custom){custom=false;requestAnimationFrame(update)}\n },true);\n new MutationObserver(function(){syncOption();requestAnimationFrame(update)}).observe(category,{childList:true});\n var list=document.getElementById('channelList');\n if(list)new MutationObserver(function(){requestAnimationFrame(update)}).observe(list,{childList:true});\n document.addEventListener('interflash-categories',function(){syncOption();requestAnimationFrame(update)});\n document.addEventListener('input',function(ev){if(ev.target&&ev.target.id==='searchChannel')requestAnimationFrame(update)},true);\n requestAnimationFrame(update);\n}\nif(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});else install();\n})();", null);
    pageReady = true;
    panelHandler.removeCallbacks(panelPoll);
    panelHandler.post(panelPoll);
   }
  });
  web.setWebChromeClient(chrome);
  WebSettings settings = web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setDomStorageEnabled(true);
  settings.setMediaPlaybackRequiresUserGesture(false);
  root.addView(web, new FrameLayout.LayoutParams(-1,-1));
  setContentView(root);
  web.loadUrl("https://inter-tv-prueba-production.up.railway.app/");
  startPanelLink();
  checkForUpdates();
 }
 private void startPanelLink() {
  SharedPreferences prefs = getSharedPreferences("interflash_tv", MODE_PRIVATE);
  panelToken = prefs.getString("device_token", null);
  if (panelToken == null || panelToken.length() != 64) {
   byte[] bytes = new byte[32];
   new java.security.SecureRandom().nextBytes(bytes);
   StringBuilder token = new StringBuilder(64);
   for (byte value : bytes) token.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
   panelToken = token.toString();
   prefs.edit().putString("device_token", panelToken).apply();
  }
  panelRevision = prefs.getInt("playlist_revision", -1);
  panelHandler.postDelayed(panelPoll, 1200);
 }

 private void syncPanel() {
  if (!pageReady || panelToken == null || !panelBusy.compareAndSet(false, true)) return;
  panelExecutor.execute(() -> {
   try {
    JSONObject registration = new JSONObject();
    registration.put("token", panelToken);
    registration.put("name", "Inter Flash TV");
    int[] status = new int[1];
    JSONObject reg = requestJson("/api/tv-native/register", "POST", null, registration, status);
    if (status[0] != 200) throw new IOException("No se pudo registrar el televisor.");
    String code = reg.optString("code", "");
    boolean approved = reg.optBoolean("approved", false);
    runOnUiThread(() -> updateLinkDialog(code, approved));
    if (!approved) return;

    int[] configStatus = new int[1];
    JSONObject config = requestJson("/api/tv-native/config", "GET", panelToken, null, configStatus);
    if (configStatus[0] == 409) {
     long now = System.currentTimeMillis();
     if (now - getSharedPreferences("interflash_tv", MODE_PRIVATE).getLong("no_playlist_notice", 0) > 90000) {
      getSharedPreferences("interflash_tv", MODE_PRIVATE).edit().putLong("no_playlist_notice", now).apply();
      runOnUiThread(() -> Toast.makeText(this, "Televisor vinculado. Guarda la lista M3U en Inter Flash TV.", Toast.LENGTH_LONG).show());
     }
     return;
    }
    if (configStatus[0] != 200) throw new IOException("No se pudo consultar la lista del panel.");
    int revision = config.optInt("revision", -1);
    if (revision < 0 || revision == panelRevision) return;
    String kind = config.optString("kind", "");
    String playlistUrl;
    if ("m3u".equals(kind)) {
     playlistUrl = config.optString("url", "");
    } else if ("xtream".equals(kind)) {
     String server = config.optString("server", "").replaceAll("/+$", "");
     if (!server.startsWith("https://")) throw new IOException("El proveedor debe usar HTTPS para sincronizarse.");
     playlistUrl = server + "/get.php?username=" + URLEncoder.encode(config.optString("username", ""), "UTF-8")
       + "&password=" + URLEncoder.encode(config.optString("password", ""), "UTF-8")
       + "&type=m3u_plus&output=mpegts";
    } else {
     throw new IOException("Formato de lista no compatible.");
    }
    String m3u = downloadPlaylist(playlistUrl);
    if (!m3u.trim().startsWith("#EXTM3U")) throw new IOException("La lista recibida no es M3U válida.");
    String quotedM3U = JSONObject.quote(m3u);
    String quotedName = JSONObject.quote(config.optString("name", "Inter Flash TV"));
    String js = "(function(){if(!window.importCustomM3U)return false;window.importCustomM3U(" + quotedM3U + "," + quotedName + ");return true;})()";
    web.post(() -> web.evaluateJavascript(js, value -> {
     if ("true".equals(value)) {
      panelRevision = revision;
      getSharedPreferences("interflash_tv", MODE_PRIVATE).edit().putInt("playlist_revision", revision).apply();
      Toast.makeText(this, "Lista sincronizada desde Inter Flash TV.", Toast.LENGTH_SHORT).show();
     }
    }));
   } catch (Exception ignored) {
   } finally {
    panelBusy.set(false);
   }
  });
 }

 private JSONObject requestJson(String path, String method, String bearer, JSONObject body, int[] statusOut) throws Exception {
  HttpURLConnection connection = (HttpURLConnection) new URL(PANEL_ROOT + path).openConnection();
  connection.setConnectTimeout(9000);
  connection.setReadTimeout(15000);
  connection.setRequestMethod(method);
  connection.setRequestProperty("Accept", "application/json");
  if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
  if (body != null) {
   connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
   try (OutputStream output = connection.getOutputStream()) {
    output.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }
  }
  int code = connection.getResponseCode();
  if (statusOut != null) statusOut[0] = code;
  InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
  String text = input == null ? "" : readText(input, 200000);
  connection.disconnect();
  return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
 }

 private String downloadPlaylist(String rawUrl) throws Exception {
  android.net.Uri uri = android.net.Uri.parse(rawUrl);
  if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IOException("La dirección de la lista debe ser HTTPS.");
  HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
  connection.setConnectTimeout(12000);
  connection.setReadTimeout(20000);
  connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, audio/mpegurl, text/plain, */*");
  connection.setRequestProperty("Accept-Encoding", "gzip");
  int code = connection.getResponseCode();
  if (code < 200 || code >= 300) {
   connection.disconnect();
   throw new IOException("El servidor no entregó la lista.");
  }
  InputStream input = connection.getInputStream();
  if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) input = new GZIPInputStream(input);
  String result = readText(input, 5 * 1024 * 1024);
  connection.disconnect();
  return result;
 }

 private String readText(InputStream input, int limit) throws IOException {
  try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
   byte[] buffer = new byte[8192];
   int size = 0, n;
   while ((n = in.read(buffer)) != -1) {
    size += n;
    if (size > limit) throw new IOException("La lista es demasiado grande.");
    out.write(buffer, 0, n);
   }
   return out.toString("UTF-8");
  }
 }

 private void updateLinkDialog(String code, boolean approved) {
  if (approved) {
   linkDialogDismissed = true;
   if (linkDialog != null && linkDialog.isShowing()) linkDialog.dismiss();
   linkDialog = null;
   linkDialogText = null;
   return;
  }
  if (code == null || code.isEmpty() || linkDialogDismissed || isFinishing()) return;
  String message = "Código para vincular la app:\n\n" + code +
    "\n\nEn la plataforma Inter Flash TV, escribe este código en «Código del televisor» y pulsa «Vincular televisor».";
  if (linkDialog != null && linkDialog.isShowing() && linkDialogText != null) {
   linkDialogText.setText(message);
   return;
  }
  linkDialogText = new TextView(this);
  linkDialogText.setText(message);
  linkDialogText.setTextSize(20);
  linkDialogText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  linkDialogText.setGravity(Gravity.CENTER);
  int padding = (int) (24 * getResources().getDisplayMetrics().density);
  linkDialogText.setPadding(padding, padding, padding, padding);
  linkDialog = new AlertDialog.Builder(this)
    .setTitle("Vincular Inter Flash TV")
    .setView(linkDialogText)
    .setPositiveButton("Cerrar", (dialog, which) -> { linkDialogDismissed = true; })
    .create();
  linkDialog.show();
 }

 @Override protected void onDestroy() {
  panelHandler.removeCallbacks(panelPoll);
  panelExecutor.shutdownNow();
  super.onDestroy();
 }

 private void checkForUpdates() {
  new Thread(() -> {
   HttpURLConnection c = null;
   try {
    c = (HttpURLConnection)new URL("https://api.github.com/repos/manuel829mg-cloud/InterFlashTV/releases?per_page=5").openConnection();
    c.setConnectTimeout(7000); c.setReadTimeout(7000);
    c.setRequestProperty("Accept","application/vnd.github+json");
    c.setRequestProperty("User-Agent","InterFlashTV-Android");
    if(c.getResponseCode()!=200)return;
    InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
    byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1&&out.size()<1000000)out.write(buf,0,n);
    in.close();
    org.json.JSONArray releases=new org.json.JSONArray(out.toString("UTF-8"));
    if(releases.length()==0)return;
    JSONObject release=releases.getJSONObject(0);
    String tag=release.optString("tag_name","");
    int latest=0;
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("v(\\d+)$").matcher(tag);
    if(m.find())latest=Integer.parseInt(m.group(1));
    if(latest<=getPackageManager().getPackageInfo(getPackageName(),0).versionCode)return;
    org.json.JSONArray assets=release.optJSONArray("assets");
    if(assets==null)return;
    String download="";
    for(int i=0;i<assets.length();i++){
     JSONObject a=assets.optJSONObject(i);
     if(a!=null&&a.optString("name").endsWith(".apk")){download=a.optString("browser_download_url");break;}
    }
    if(download.isEmpty())return;
    final String url=download;
    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Actualización de Inter Flash TV")
      .setMessage("Hay una nueva versión disponible. ¿Quieres actualizar ahora?")
      .setNegativeButton("Más tarde",null)
      .setPositiveButton("Actualizar ahora",(d,w)->{
       Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
       startActivity(intent);
      }).show());
   }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
  }).start();
 }
 private void confirmExit() {
  if (isFinishing() || (exitDialog != null && exitDialog.isShowing())) return;
  exitDialog = new AlertDialog.Builder(this)
   .setTitle("Salir de Inter Flash TV")
   .setMessage("¿Quieres salir de la aplicación?")
   .setNegativeButton("Cancelar", (dialog, which) -> {})
   .setPositiveButton("Salir", (dialog, which) -> finish())
   .create();
  exitDialog.setOnShowListener(dialog -> exitDialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());
  exitDialog.show();
 }
 @Override public void onBackPressed() {
  if (exitDialog != null && exitDialog.isShowing()) return;
  if (fullscreenView != null) { exitFullscreen(); return; }
  if (checkingBack) return;
  checkingBack = true;
  web.evaluateJavascript("(function(){var p=document.getElementById('player'),t=document.getElementById('teleantillasViewport');if((p&&p.classList.contains('tv-fullscreen'))||(t&&t.classList.contains('tv-fullscreen'))||document.fullscreenElement){if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen();if(window.interFlashBack)window.interFlashBack();return true;}return false;})()", value -> {
   checkingBack = false;
   if (!"true".equals(value)) confirmExit();
  });
 }
}
