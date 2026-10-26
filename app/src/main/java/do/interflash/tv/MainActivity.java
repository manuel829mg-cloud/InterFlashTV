package com.interflash.tv;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
    view.evaluateJavascript("(function(){if(document.getElementById('interflash-tv-fit'))return;var s=document.createElement('style');s.id='interflash-tv-fit';s.textContent='video:fullscreen, video:-webkit-full-screen {width:100vw!important;height:100vh!important;object-fit:cover!important;}#m3uImport,#m3ufile,.tv-channels>p.muted:not(#count){display:none!important}.tv-channels{padding:10px!important}.tv-channels>h2{font-size:20px!important;margin:0 0 8px!important}#searchChannel{box-sizing:border-box;width:100%!important;min-width:0!important;padding:8px!important;margin:0 0 6px!important;font-size:16px!important}#count{font-size:12px!important;margin:3px 0 6px!important}#channelList{gap:2px!important}#channelList button{box-sizing:border-box;min-height:36px!important;padding:6px 9px!important;margin:1px 0!important;font-size:16px!important;line-height:1.25!important;border-radius:6px!important;width:100%!important}#channelList button img{width:28px!important;height:24px!important;margin-right:6px!important}';document.head.appendChild(s);var heading=document.querySelector(\'.tv-channels>h2\');if(heading)heading.textContent=\'Canales\';})();", null);
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
  checkForUpdates();
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
  web.evaluateJavascript("(function(){var p=document.getElementById('player'),t=document.getElementById('teleantillasViewport');if((p&&p.classList.contains('tv-fullscreen'))||(t&&t.classList.contains('tv-fullscreen'))||document.fullscreenElement){if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen();if(window.interFlashBack)window.interFlashBack();return true;}var dialog=document.getElementById('playlistDialog')||document.getElementById('streamConsent');if(dialog){var cancel=dialog.querySelector('button');if(cancel)cancel.click();return true;}var drawer=document.querySelector('.reference-categories.categories-open');if(drawer){drawer.classList.remove('categories-open');var categories=document.getElementById('designCategories');if(categories){categories.setAttribute('aria-expanded','false');categories.focus();}return true;}var section=document.body.dataset.libraryScreen;if(section&&section!=='home'&&window.interflashNavigate){window.interflashNavigate('home');return true;}return false;})()", value -> {
   checkingBack = false;
   if (!"true".equals(value)) confirmExit();
  });
 }
}
