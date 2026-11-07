package org.bols.minecraft;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://bols.dpdns.org/";
    private static final int AUDIO_PERMISSION_REQUEST = 1201;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1202;
    private static final int BG = Color.rgb(8, 18, 18);
    private static final int TEXT = Color.rgb(239, 249, 245);
    private static final int MUTED = Color.rgb(139, 169, 160);
    private static final int MINT = Color.rgb(112, 237, 180);

    private WebView webView;
    private ProgressBar progressBar;
    private TextView pageTitle;
    private PermissionRequest pendingWebPermission;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(Color.rgb(7, 15, 17));
        if (Build.VERSION.SDK_INT >= 23) w.getDecorView().setSystemUiVisibility(0);
        buildInterface();
        configureWebView();
        String notificationUrl = getIntent().getStringExtra("notification_url");
        if (isNativeLoginCallback(getIntent())) {
            webView.loadUrl(HOME_URL);
            completeNativeLogin(getIntent().getData());
        } else if (notificationUrl != null && !notificationUrl.isEmpty()) {
            webView.loadUrl(getSafeNotificationUrl(notificationUrl));
        } else if (state != null) {
            webView.restoreState(state);
        } else {
            webView.loadUrl(HOME_URL);
        }
        requestNotificationPermissionIfNeeded();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(13), dp(7), dp(13), dp(9));
        root.setFitsSystemWindows(true);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(8, 18, 18), Color.rgb(12, 27, 28), Color.rgb(7, 15, 18)}));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(3), dp(4), dp(3), dp(10));
        TextView logo = new TextView(this);
        logo.setText("B");
        logo.setGravity(Gravity.CENTER);
        logo.setTextSize(23);
        logo.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        logo.setTextColor(Color.rgb(7, 25, 20));
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(139, 250, 197), Color.rgb(48, 183, 137)});
        logoBg.setCornerRadius(dp(16));
        logoBg.setStroke(dp(1), Color.argb(100, 220, 255, 238));
        logo.setBackground(logoBg);
        logo.setElevation(dp(4));
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("無垠伺服器");
        title.setTextColor(TEXT);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        TextView brand = new TextView(this);
        brand.setText("BOLS COMMUNITY  ·  MINECRAFT");
        brand.setTextColor(MUTED);
        brand.setTextSize(9);
        brand.setLetterSpacing(0.08f);
        brand.setPadding(0, dp(2), 0, 0);
        pageTitle = new TextView(this);
        pageTitle.setText("正在連線至社群…");
        pageTitle.setTextColor(MINT);
        pageTitle.setTextSize(11);
        pageTitle.setPadding(0, dp(3), 0, 0);
        names.addView(title);
        names.addView(brand);
        names.addView(pageTitle);
        header.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        TextView online = new TextView(this);
        online.setText("● APP");
        online.setTextColor(Color.rgb(175, 243, 206));
        online.setTextSize(10);
        online.setTypeface(Typeface.DEFAULT_BOLD);
        online.setPadding(dp(10), dp(8), dp(11), dp(8));
        online.setBackground(shape(Color.rgb(18, 54, 43), dp(30), Color.rgb(43, 100, 75)));
        header.addView(online);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(33, 60, 57));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.bottomMargin = dp(9);
        root.addView(divider, dividerLp);

        FrameLayout webFrame = new FrameLayout(this);
        webFrame.setBackground(shape(Color.WHITE, dp(21), Color.rgb(53, 84, 79)));
        webFrame.setClipToOutline(true);
        webFrame.setElevation(dp(5));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webFrame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        webFrame.addView(progressBar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        webLp.bottomMargin = dp(11);
        root.addView(webFrame, webLp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(7), dp(7), dp(7), dp(7));
        nav.setBackground(shape(Color.rgb(17, 33, 34), dp(23), Color.rgb(43, 72, 68)));
        nav.setElevation(dp(8));
        nav.addView(navItem("⌂", "首頁", true, () -> webView.loadUrl(HOME_URL)), weight());
        nav.addView(navItem("‹", "返回", false, () -> {
            if (webView.canGoBack()) webView.goBack();
            else Toast.makeText(this, "已經在首頁", Toast.LENGTH_SHORT).show();
        }), weight());
        nav.addView(navItem("↻", "重新整理", false, () -> webView.reload()), weight());
        nav.addView(navItem("↗", "瀏覽器", false, () -> openCurrentPage()), weight());
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, dp(57), 1f);
    }

    private View navItem(String symbol, String label, boolean selected, Runnable click) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(3), dp(4), dp(3), dp(3));
        item.setClickable(true);
        item.setFocusable(true);
        item.setBackground(shape(selected ? Color.rgb(29, 76, 60) : Color.TRANSPARENT,
                dp(16), selected ? Color.rgb(60, 128, 94) : Color.TRANSPARENT));
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(23);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(selected ? MINT : Color.rgb(151, 181, 174));
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setTextColor(selected ? Color.rgb(202, 255, 224) : MUTED);
        item.addView(icon, new LinearLayout.LayoutParams(-1, dp(29)));
        item.addView(text, new LinearLayout.LayoutParams(-1, dp(16)));
        item.setOnClickListener(v -> click.run());
        return item;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private void openCurrentPage() {
        try {
            Uri target = Uri.parse(webView.getUrl() == null ? HOME_URL : webView.getUrl());
            if (!isSiteUrl(target.toString())) target = Uri.parse(HOME_URL);
            startActivity(new Intent(Intent.ACTION_VIEW, target));
        } catch (Exception e) {
            Toast.makeText(this, "目前無法開啟外部瀏覽器。", Toast.LENGTH_SHORT).show();
        }
    }

    private void configureWebView() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setAllowFileAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(settings.getUserAgentString() + " BolsMinecraftApp/1.2");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                String path = uri.getPath() == null ? "" : uri.getPath();
                if ("auth.bols.dpdns.org".equals(host) && ("/login".equals(path) || "/auth/google".equals(path))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri.buildUpon().appendQueryParameter("client", "android").build())); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, "無法開啟瀏覽器進行 Google 登入。", Toast.LENGTH_LONG).show(); }
                    return true;
                }
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    if (host.equals("bols.dpdns.org") || host.endsWith(".bols.dpdns.org") || host.equals("auth.bols.dpdns.org") || host.equals("discord.gg") || host.endsWith(".google.com") || host.equals("accounts.google.com") || host.equals("tw-6.cs-nodes.net")) return false;
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return false;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                progressBar.setVisibility(View.VISIBLE);
                pageTitle.setText("正在連線至社群…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                pageTitle.setText("BOLS COMMUNITY  ·  已連線");
                if (isSiteUrl(url)) syncFcmTokenToWebsite();
                String pending = getIntent().getStringExtra("notification_url");
                if (pending != null && !pending.isEmpty() && isSiteUrl(url)) getIntent().removeExtra("notification_url");
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progressBar.setProgress(value);
                progressBar.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                boolean audio = false;
                for (String resource : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audio = true;
                if (!audio) { request.deny(); return; }
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingWebPermission = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
                } else request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }
        });
    }

    private void syncFcmTokenToWebsite() {
        if (webView == null || FirebaseApp.getApps(this).isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || webView == null) return;
            String token;
            try { token = JSONObject.quote(task.getResult()); } catch (Exception e) { return; }
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript("if (window.registerAndroidPushToken) window.registerAndroidPushToken(" + token + ");", null);
            });
        });
    }

    private boolean isSiteUrl(String candidate) {
        if (candidate == null) return false;
        Uri uri = Uri.parse(candidate);
        return "https".equalsIgnoreCase(uri.getScheme()) && "bols.dpdns.org".equalsIgnoreCase(uri.getHost());
    }

    private String getSafeNotificationUrl(String candidate) {
        return isSiteUrl(candidate) ? candidate : HOME_URL;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        new AlertDialog.Builder(this).setTitle("開啟推播通知")
            .setMessage("允許通知後，無垠伺服器可以在有新公告或有人 @提及你時通知你。你可以在系統設定中隨時關閉。")
            .setNegativeButton("稍後", (d, w) -> d.dismiss())
            .setPositiveButton("允許", (d, w) -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST)).show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isNativeLoginCallback(intent)) {
            if (webView != null) webView.loadUrl(HOME_URL);
            completeNativeLogin(intent.getData());
            return;
        }
        String target = getSafeNotificationUrl(intent.getStringExtra("notification_url"));
        if (webView != null) webView.loadUrl(target);
    }

    private boolean isNativeLoginCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri data = intent.getData();
        return "bolsminecraft".equalsIgnoreCase(data.getScheme()) && "auth".equalsIgnoreCase(data.getHost()) && "/callback".equals(data.getPath());
    }

    private void completeNativeLogin(Uri callback) {
        String code = callback == null ? null : callback.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, "登入交接資料不完整，請重新登入。", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://auth.bols.dpdns.org/api/app-auth/exchange").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] body = new JSONObject().put("code", code).toString().getBytes("UTF-8");
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder responseText = new StringBuilder();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) responseText.append(line);
                }
                JSONObject response = new JSONObject(responseText.toString());
                String session = response.optString("session", "");
                if (status < 200 || status >= 300 || !response.optBoolean("ok", false) || session.isEmpty()) {
                    String error = response.optString("error", "登入驗證失敗，請重新登入。");
                    runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_LONG).show());
                    return;
                }
                CookieManager manager = CookieManager.getInstance();
                manager.setAcceptCookie(true);
                String cookie = "session=" + session + "; Domain=.bols.dpdns.org; Path=/; Max-Age=604800; Secure; HttpOnly; SameSite=Lax";
                manager.setCookie(HOME_URL, cookie, accepted -> {
                    manager.flush();
                    runOnUiThread(() -> {
                        if (webView != null) webView.loadUrl(HOME_URL);
                        Toast.makeText(this, Boolean.TRUE.equals(accepted) ? "Google 登入成功。" : "登入完成，但無法保存登入狀態，請重新登入。", Toast.LENGTH_LONG).show();
                    });
                });
            } catch (Exception e) {
                String error = "App 登入失敗：" + (e.getMessage() == null ? "無法連線到登入服務" : e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION_REQUEST && pendingWebPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else {
                pendingWebPermission.deny();
                Toast.makeText(this, "未允許麥克風權限，語音功能無法使用。", Toast.LENGTH_LONG).show();
            }
            pendingWebPermission = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (webView != null) webView.saveState(out);
    }
    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
