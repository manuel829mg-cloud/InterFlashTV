package com.interflash.tv;
import android.app.Activity;
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
 private boolean returnedToMenu = false;
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
    view.evaluateJavascript("(function(){if(document.getElementById('interflash-tv-fit'))return;var s=document.createElement('style');s.id='interflash-tv-fit';s.textContent='video:fullscreen, video:-webkit-full-screen {width:100vw!important;height:100vh!important;object-fit:cover!important;}';document.head.appendChild(s);})();", null);
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
 }
 @Override public void onBackPressed() {
  if (!returnedToMenu) {
   if (fullscreenView != null) exitFullscreen();
   web.evaluateJavascript("if(window.interFlashBack){window.interFlashBack()}else{var v=document.getElementById('player');if(v)v.classList.remove('tv-fullscreen');var list=document.getElementById('channelList');if(list)list.scrollIntoView();}", null);
   returnedToMenu = true;
  } else {
   finish();
  }
 }
}
