package com.interflash.tv.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Solo consulta la disponibilidad de versiones: NO instala ni descarga APK.
 * Integrar en MainActivity después de configurar un endpoint HTTPS propio.
 * Usar siempre un manifiesto del canal "stable" para clientes.
 */
public final class VersionChecker {
    public interface Listener {
        void onNewVersion(int versionCode, String versionName, String apkUrl, String sha256);
        void onUpToDate();
        void onError(String description);
    }
    private VersionChecker() {}

    public static void check(Context context, String manifestUrl, Listener listener) {
        if (manifestUrl == null || !manifestUrl.startsWith("https://")) {
            listener.onError("Se requiere un manifiesto HTTPS");
            return;
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(manifestUrl);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new Exception("URL no HTTPS");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() != 200) throw new Exception("Servidor de actualizaciones no disponible");
                StringBuilder body = new StringBuilder();
                try (InputStream in = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (body.length() + line.length() > 32768) throw new Exception("Manifiesto demasiado grande");
                        body.append(line);
                    }
                }
                JSONObject json = new JSONObject(body.toString());
                int remoteCode = json.getInt("versionCode");
                String remoteName = json.getString("versionName");
                String apk = json.getString("apkUrl");
                String sha = json.getString("sha256");
                if (!apk.startsWith("https://") || !sha.matches("[A-Fa-f0-9]{64}"))
                    throw new Exception("Metadatos de actualización incorrectos");
                PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                long localCode = android.os.Build.VERSION.SDK_INT >= 28
                    ? installed.getLongVersionCode() : installed.versionCode;
                if (remoteCode > localCode) main.post(() -> listener.onNewVersion(remoteCode, remoteName, apk, sha));
                else main.post(listener::onUpToDate);
            } catch (Exception e) {
                main.post(() -> listener.onError(e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
                worker.shutdown();
            }
        });
    }
}
