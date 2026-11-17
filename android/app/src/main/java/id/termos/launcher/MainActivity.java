package id.termos.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.webkit.GeolocationPermissions;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * termOS launcher: WebView yang memuat web app termOS dari assets aplikasi
 * (lewat https://appassets.androidplatform.net supaya WebCrypto aktif),
 * plus jembatan "Android" untuk membuka aplikasi, menyimpan file, mencetak,
 * dan membaca riwayat panggilan (hanya setelah pengguna mengizinkan).
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILE = 1;
    private static final int REQ_PERM = 2;

    private WebView web;
    private WebView printView;
    private ValueCallback<Uri[]> fileCb;
    private String permWhat = "";
    private static final int REQ_GEO = 3;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    private int notifId = 1;
    // host RSS berita yang boleh diambil lewat jembatan (bebas CORS)
    private static final List<String> RSS_HOSTS = Arrays.asList(
            "www.antaranews.com", "www.cnnindonesia.com", "news.detik.com", "rss.tempo.co",
            "feeds.bbci.co.uk", "www.republika.co.id", "www.voaindonesia.com");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int bg = Color.parseColor("#060a07");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        web = new WebView(this);
        web.setBackgroundColor(bg);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("termos-alert", "Peringatan termOS", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Gempa, cuaca ekstrem, kurs, berita");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openExternal(u.toString()); // link luar dibuka di aplikasi/browser lain
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    geoCb = cb; geoOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                List<String> mimes = new ArrayList<>();
                String[] accept = p.getAcceptTypes() != null ? p.getAcceptTypes() : new String[0];
                for (String t : accept) {
                    for (String part : t.split(",")) {
                        String m = part.trim();
                        if (m.contains("/")) mimes.add(m);
                    }
                }
                if (!mimes.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "Pilih file"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("https://" + HOST + "/assets/termos/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onResumeApp && window.onResumeApp()", null);
    }

    @Override
    public void onBackPressed() {
        // sebagai launcher, tombol kembali tidak menutup aplikasi
        web.evaluateJavascript("window.onBack && window.onBack()", null);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int k = 0; k < n; k++) result[k] = data.getClipData().getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCb.onReceiveValue(result);
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_GEO) {
            if (geoCb != null) geoCb.invoke(geoOrigin, ok, false);
            geoCb = null;
            return;
        }
        if (req != REQ_PERM) return;
        web.evaluateJavascript("window.onAndroidPerm && window.onAndroidPerm('" + permWhat + "'," + ok + ")", null);
    }

    private void openExternal(String url) {
        try {
            Intent i = url.startsWith("intent:")
                    ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            // tidak ada aplikasi yang bisa membuka tautan ini
        }
    }

    private String permFor(String what) {
        if ("calls".equals(what)) return Manifest.permission.READ_CALL_LOG;
        if ("loc".equals(what)) return Manifest.permission.ACCESS_FINE_LOCATION;
        if ("notif".equals(what)) return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.POST_NOTIFICATIONS : "";
        return "";
    }

    /** Fungsi yang bisa dipanggil dari JavaScript lewat window.Android */
    class Bridge {
        @JavascriptInterface
        public String getApps() {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            JSONArray arr = new JSONArray();
            for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                if (getPackageName().equals(r.activityInfo.packageName)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("l", r.loadLabel(pm).toString());
                    o.put("p", r.activityInfo.packageName);
                    arr.put(o);
                } catch (Exception ignored) { }
            }
            return arr.toString();
        }

        @JavascriptInterface
        public boolean launch(String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(() -> openExternal(url));
        }

        @JavascriptInterface
        public boolean isDefaultLauncher() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && getPackageName().equals(r.activityInfo.packageName);
        }

        @JavascriptInterface
        public void openSettings(final String which) {
            runOnUiThread(() -> {
                Intent i = "home".equals(which)
                        ? new Intent(Settings.ACTION_HOME_SETTINGS)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            });
        }

        @JavascriptInterface
        public String saveFile(String b64, String name, String mime) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/termOS");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "ERR: tidak bisa membuat file";
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) return "ERR: tidak bisa menulis file";
                    os.write(data);
                }
                return "Download/termOS/" + name;
            } catch (Exception e) {
                return "ERR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("termOS", text));
            });
        }

        @JavascriptInterface
        public void printHtml(final String html, final String name) {
            runOnUiThread(() -> {
                WebView pv = new WebView(MainActivity.this);
                pv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView v, String url) {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        if (pm != null) pm.print(name, v.createPrintDocumentAdapter(name),
                                new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                    }
                });
                pv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                printView = pv; // simpan referensi selama mencetak
            });
        }

        @JavascriptInterface
        public boolean hasPerm(String what) {
            String p = permFor(what);
            if (p.isEmpty()) return "notif".equals(what); // notifikasi otomatis diizinkan di Android < 13
            return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
        }

        /** Notifikasi peringatan (gempa, cuaca, kurs, berita). Ketuk = buka termOS. */
        @JavascriptInterface
        public void showAlert(final String title, final String text) {
            String p = permFor("notif");
            if (!p.isEmpty() && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return;
            Intent open = new Intent(MainActivity.this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(MainActivity.this, "termos-alert")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(notifId++, n);
        }

        /** Ambil RSS berita dari daftar host tetap (tidak bisa dipakai untuk URL lain). */
        @JavascriptInterface
        public String fetchText(String url) {
            try {
                URL u = new URL(url);
                if (!"https".equals(u.getProtocol()) || !RSS_HOSTS.contains(u.getHost())) return "ERR: host tidak diizinkan";
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "termOS/3.2 (Android)");
                if (c.getResponseCode() != 200) return "ERR: HTTP " + c.getResponseCode();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null && sb.length() < 2_000_000) sb.append(line).append('\n');
                }
                return sb.toString();
            } catch (Exception e) {
                return "ERR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void requestPerm(final String what) {
            final String p = permFor(what);
            if (p.isEmpty()) return;
            permWhat = what;
            runOnUiThread(() -> requestPermissions(new String[]{p}, REQ_PERM));
        }

        /** Riwayat panggilan: nomor, jenis, waktu, durasi. Nama kontak TIDAK dibaca. */
        @JavascriptInterface
        public String readCallLog(int limit) {
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return "ERR: izin belum diberikan";
            JSONArray arr = new JSONArray();
            String[] cols = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION};
            try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, cols, null, null, CallLog.Calls.DATE + " DESC")) {
                int n = 0;
                while (c != null && c.moveToNext() && n++ < limit) {
                    JSONObject o = new JSONObject();
                    o.put("num", c.getString(0));
                    o.put("type", c.getInt(1));
                    o.put("t", c.getLong(2));
                    o.put("dur", c.getLong(3));
                    arr.put(o);
                }
            } catch (Exception e) {
                return "ERR: " + e.getMessage();
            }
            return arr.toString();
        }
    }
}
