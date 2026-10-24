package do.interflash.tv;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.View;
public class MainActivity extends Activity {
 WebView web;
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);web=new WebView(this);web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setMediaPlaybackRequiresUserGesture(false);web.loadUrl("https://inter-tv-prueba-production.up.railway.app/");setContentView(web);}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
