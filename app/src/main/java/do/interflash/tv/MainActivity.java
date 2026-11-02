package com.interflash.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/** Local Inter Flash TV player. The panel supplies only the paired playlist. */
public final class MainActivity extends Activity {
    private static final String PANEL_ROOT = "https://interflash-isp-manager.up.railway.app";
    private static final String PREFS = "interflash_tv_player";
    private static final int BG = Color.rgb(10, 14, 24);
    private static final int CARD = Color.rgb(25, 32, 48);
    private static final int PURPLE = Color.rgb(172, 74, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final List<Channel> channels = new ArrayList<>();
    private String deviceToken;
    private int revision = -1;
    private String selectedCategory = "Todos";
    private String currentName = "";
    private boolean linkDialogDismissed;

    private FrameLayout root;
    private LinearLayout body;
    private LinearLayout categoryRow;
    private LinearLayout channelRows;
    private TextView status;
    private TextView nowPlaying;
    private PlayerView playerView;
    private ExoPlayer player;
    private AlertDialog linkDialog;
    private TextView linkText;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            syncPanel();
            handler.postDelayed(this, 8000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        buildScreen();
        startPairing();
        handler.postDelayed(poll, 1000);
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(28), dp(16), dp(28), dp(22));
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));

        TextView title = label("INTER FLASH TV", 26, Color.WHITE, true);
        body.addView(title, margins(0, 0, 0, 5));
        status = label("Conectando con Inter Flash…", 15, Color.LTGRAY, false);
        body.addView(status, margins(0, 0, 0, 12));

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setFocusable(true);
        playerView.setFocusableInTouchMode(true);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.BLACK);
        frame.setCornerRadius(dp(10));
        playerView.setBackground(frame);
        body.addView(playerView, new LinearLayout.LayoutParams(-1, dp(300)));

        nowPlaying = label("Elige un canal para comenzar", 17, Color.WHITE, true);
        body.addView(nowPlaying, margins(0, 9, 0, 9));

        body.addView(label("Categorías", 15, Color.LTGRAY, true), margins(0, 0, 0, 3));
        HorizontalScrollView categories = new HorizontalScrollView(this);
        categories.setHorizontalScrollBarEnabled(false);
        categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categories.addView(categoryRow);
        body.addView(categories, margins(0, 0, 0, 8));

        body.addView(label("Canales", 15, Color.LTGRAY, true), margins(0, 0, 0, 3));
        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(false);
        channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(channelRows);
        body.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        renderChannels();
    }

    private void startPairing() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        deviceToken = prefs.getString("device_token", null);
        if (deviceToken == null || deviceToken.length() != 64) {
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            StringBuilder token = new StringBuilder(64);
            for (byte value : bytes) token.append(String.format(Locale.US, "%02x", value & 0xff));
            deviceToken = token.toString();
            prefs.edit().putString("device_token", deviceToken).apply();
        }
        revision = prefs.getInt("playlist_revision", -1);
    }

    private void syncPanel() {
        if (isFinishing() || !busy.compareAndSet(false, true)) return;
        network.execute(() -> {
            try {
                JSONObject registration = new JSONObject();
                registration.put("token", deviceToken);
                registration.put("name", "Inter Flash TV");
                int[] statusCode = new int[1];
                JSONObject registered = requestJson("/api/tv-native/register", "POST", null, registration, statusCode);
                if (statusCode[0] != 200) throw new IOException("Registro no disponible (" + statusCode[0] + ")");
                String code = registered.optString("code", "");
                boolean approved = registered.optBoolean("approved", false);
                runOnUiThread(() -> updateLinkDialog(code, approved));
                if (!approved) {
                    runOnUiThread(() -> setStatus("Vincula este televisor desde la plataforma Inter Flash."));
                    return;
                }

                int[] configCode = new int[1];
                JSONObject config = requestJson("/api/tv-native/config", "GET", deviceToken, null, configCode);
                if (configCode[0] == 409) {
                    runOnUiThread(() -> setStatus("Televisor vinculado. Agrega o guarda la lista de canales en el panel de TV."));
                    return;
                }
                if (configCode[0] != 200) throw new IOException("No se pudo leer la lista del panel (" + configCode[0] + ")");
                int newRevision = config.optInt("revision", -1);
                if (newRevision < 0 || newRevision == revision) return;
                String m3u = downloadPlaylist(playlistUrl(config));
                List<Channel> parsed = parseM3u(m3u);
                if (parsed.isEmpty()) throw new IOException("La lista del panel no contiene canales válidos.");
                runOnUiThread(() -> {
                    channels.clear();
                    channels.addAll(parsed);
                    revision = newRevision;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("playlist_revision", revision).apply();
                    selectedCategory = "Todos";
                    renderChannels();
                    setStatus("Lista sincronizada: " + channels.size() + " canales. Se actualizará automáticamente.");
                    if (currentName.isEmpty() && !channels.isEmpty()) playChannel(channels.get(0));
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Error al sincronizar la lista." : e.getMessage();
                runOnUiThread(() -> setStatus(message));
            } finally {
                busy.set(false);
            }
        });
    }

    private String playlistUrl(JSONObject config) throws Exception {
        String kind = config.optString("kind", "");
        if ("m3u".equals(kind)) return config.optString("url", "");
        if ("xtream".equals(kind)) {
            String server = config.optString("server", "").replaceAll("/+$", "");
            if (!server.startsWith("https://")) throw new IOException("El proveedor debe usar HTTPS para sincronizarse.");
            return server + "/get.php?username=" + URLEncoder.encode(config.optString("username", ""), "UTF-8")
                    + "&password=" + URLEncoder.encode(config.optString("password", ""), "UTF-8")
                    + "&type=m3u_plus&output=mpegts";
        }
        throw new IOException("El formato de lista del panel no es compatible.");
    }

    private JSONObject requestJson(String path, String method, String bearer, JSONObject body, int[] codeOut) throws Exception {
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
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        if (codeOut != null) codeOut[0] = code;
        InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String result = input == null ? "" : readText(input, 200000);
        connection.disconnect();
        return result.trim().isEmpty() ? new JSONObject() : new JSONObject(result);
    }

    private String downloadPlaylist(String rawUrl) throws Exception {
        Uri uri = Uri.parse(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            throw new IOException("La dirección de la lista debe usar HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, audio/mpegurl, text/plain, */*");
        connection.setRequestProperty("Accept-Encoding", "gzip");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("El servidor de la lista respondió " + code + ".");
        }
        InputStream input = connection.getInputStream();
        if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) input = new GZIPInputStream(input);
        String result = readText(input, 5 * 1024 * 1024);
        connection.disconnect();
        if (!result.trim().startsWith("#EXTM3U")) throw new IOException("El panel no entregó una lista M3U válida.");
        return result;
    }

    private String readText(InputStream input, int limit) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count, size = 0;
            while ((count = in.read(buffer)) != -1) {
                size += count;
                if (size > limit) throw new IOException("La lista recibida es demasiado grande.");
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        }
    }

    private List<Channel> parseM3u(String text) {
        List<Channel> parsed = new ArrayList<>();
        String name = null, group = "General", logo = "";
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF:")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Canal";
                group = attribute(line, "group-title", "General");
                logo = attribute(line, "tvg-logo", "");
            } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                Uri uri = Uri.parse(line);
                if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null)
                    parsed.add(new Channel(name.isEmpty() ? "Canal" : name, group.isEmpty() ? "General" : group, logo, line));
                name = null;
                group = "General";
                logo = "";
            }
        }
        return parsed;
    }

    private String attribute(String line, String key, String fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\s)" + java.util.regex.Pattern.quote(key) + "=\"([^\"]*)\"").matcher(line);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private void updateLinkDialog(String code, boolean approved) {
        if (approved) {
            if (linkDialog != null && linkDialog.isShowing()) linkDialog.dismiss();
            linkDialog = null;
            return;
        }
        if (code == null || code.isEmpty() || linkDialogDismissed || isFinishing()) return;
        String message = "Código para vincular el televisor:\n\n" + code
                + "\n\nEn Inter Flash, escríbelo en «Código del televisor» y pulsa «Vincular televisor».";
        if (linkDialog != null && linkDialog.isShowing() && linkText != null) {
            linkText.setText(message);
            return;
        }
        linkText = label(message, 20, Color.WHITE, true);
        linkText.setGravity(Gravity.CENTER);
        linkText.setPadding(dp(20), dp(20), dp(20), dp(20));
        linkDialog = new AlertDialog.Builder(this)
                .setTitle("Vincular Inter Flash TV")
                .setView(linkText)
                .setPositiveButton("Cerrar", (dialog, which) -> linkDialogDismissed = true)
                .create();
        linkDialog.show();
    }

    private void renderChannels() {
        if (categoryRow == null || channelRows == null) return;
        categoryRow.removeAllViews();
        channelRows.removeAllViews();
        Set<String> categories = new LinkedHashSet<>();
        for (Channel channel : channels) categories.add(channel.group);
        addCategory("Todos");
        for (String category : categories) addCategory(category);
        int count = 0;
        for (Channel channel : channels) {
            if (!"Todos".equals(selectedCategory) && !selectedCategory.equals(channel.group)) continue;
            Button button = new Button(this);
            button.setText(channel.name);
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);
            button.setAllCaps(false);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setBackground(tint(CARD, dp(8)));
            button.setFocusable(true);
            button.setOnClickListener(v -> playChannel(channel));
            channelRows.addView(button, new LinearLayout.LayoutParams(-1, dp(48)));
            count++;
        }
        if (channels.isEmpty()) {
            TextView empty = label("Esperando la lista de canales del panel…", 16, Color.LTGRAY, false);
            channelRows.addView(empty, margins(4, 8, 0, 8));
        } else if (count == 0) {
            selectedCategory = "Todos";
            renderChannels();
        }
    }

    private void addCategory(String name) {
        Button button = new Button(this);
        button.setText(name);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(tint(name.equals(selectedCategory) ? PURPLE : CARD, dp(20)));
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            selectedCategory = name;
            renderChannels();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.setMargins(0, 0, dp(7), 0);
        categoryRow.addView(button, lp);
    }

    private void playChannel(Channel channel) {
        try {
            if (player != null) player.release();
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    setStatus("Este canal no respondió. Prueba otro o revisa su enlace en el panel.");
                }
            });
            MediaItem.Builder item = new MediaItem.Builder().setUri(channel.url);
            if (channel.url.toLowerCase(Locale.ROOT).contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
            player.setMediaItem(item.build());
            player.prepare();
            player.play();
            currentName = channel.name;
            nowPlaying.setText("Reproduciendo: " + channel.name);
            setStatus(channels.size() + " canales disponibles");
        } catch (Exception e) {
            setStatus("No se pudo abrir este canal.");
        }
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable tint(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && player != null && player.isPlaying()) {
            player.pause();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(poll);
        network.shutdownNow();
        if (player != null) player.release();
        super.onDestroy();
    }

    private static final class Channel {
        final String name;
        final String group;
        final String logo;
        final String url;
        Channel(String name, String group, String logo, String url) {
            this.name = name;
            this.group = group;
            this.logo = logo;
            this.url = url;
        }
    }
}
