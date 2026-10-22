package tech.huangliji.shell;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 浏览器外壳：全屏 WebView，只允许访问 BuildConfig.ALLOWED_HOSTS 内的域名，
 * 避免店员误跳到其他网址。
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_PERM = 1002;

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorView;
    private TextView errorText;
    private Set<String> allowedHosts;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;
    private long lastBackPress = 0;
    private boolean mainFrameError = false;
    private WebView printView; // 保持引用直到打印任务创建完成

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        allowedHosts = new HashSet<>();
        for (String h : BuildConfig.ALLOWED_HOSTS.split(",")) {
            if (!h.trim().isEmpty()) allowedHosts.add(h.trim().toLowerCase(Locale.ROOT));
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        root.addView(progress, plp);

        errorView = buildErrorView();
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        hideSystemUi();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // 新订单提示音
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " ZhidianShell/" + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.APP_KEY + "; android)");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NativeBridge(), "ZhidianNative");
        webView.setWebViewClient(new ShellClient());
        webView.setWebChromeClient(new ShellChrome());
        webView.setDownloadListener((url, ua, cd, mime, len) ->
                toast("此应用不支持下载文件"));

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(BuildConfig.START_URL);
        }
    }

    // ---------------------------------------------------------------- 导航限制

    private boolean isAllowed(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("about") || scheme.equals("blob")) return true;
        if (!scheme.equals("https")) return false;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return allowedHosts.contains(host);
    }

    private class ShellClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            if (isAllowed(request.getUrl())) return false;
            toast("已拦截：此应用只能访问 " + String.join("、", allowedHosts));
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mainFrameError = false;
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            if (!mainFrameError) errorView.setVisibility(View.GONE);
            view.evaluateJavascript(INJECT_JS, null);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                mainFrameError = true;
                showError(isOnline() ? "页面加载失败（" + error.getDescription() + "）" : "网络未连接，请检查 Wi-Fi 或网线");
            }
        }
    }

    private class ShellChrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                toast("没有可用的文件选择器");
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                Uri origin = request.getOrigin();
                if (!isAllowed(origin)) {
                    request.deny();
                    return;
                }
                List<String> need = new ArrayList<>();
                List<String> res = Arrays.asList(request.getResources());
                if (res.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                        && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    need.add(Manifest.permission.CAMERA);
                }
                if (res.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    need.add(Manifest.permission.RECORD_AUDIO);
                }
                if (need.isEmpty()) {
                    request.grant(request.getResources());
                } else {
                    pendingPermission = request;
                    requestPermissions(need.toArray(new String[0]), REQ_PERM);
                }
            });
        }
    }

    // ---------------------------------------------------------------- 打印 / window.open 兼容

    /** 页面里的 window.print() 与 window.open('') + document.write 打印小票，转给系统打印服务。 */
    private static final String INJECT_JS = "(function(){" +
            "if(window.__zdShell)return;window.__zdShell=1;" +
            "window.print=function(){ZhidianNative.printHtml(document.documentElement.outerHTML,document.title||'')};" +
            "var _o=window.open;" +
            "window.open=function(u,t,f){" +
            " if(!u||u==='about:blank'){var h='';var w={closed:false,document:{write:function(s){h+=s},writeln:function(s){h+=s+'\\n'},open:function(){h=''},close:function(){}}," +
            "  print:function(){ZhidianNative.printHtml(h,'')},close:function(){w.closed=true},focus:function(){},blur:function(){}};return w;}" +
            " try{location.href=new URL(u,location.href).href}catch(e){}return window;};" +
            "})();";

    private class NativeBridge {
        @JavascriptInterface
        public void printHtml(final String html, final String title) {
            runOnUiThread(() -> doPrint(html, title));
        }

        @JavascriptInterface
        public String appInfo() {
            return "{\"app\":\"" + BuildConfig.APP_KEY + "\",\"version\":\"" + BuildConfig.VERSION_NAME + "\",\"platform\":\"android\"}";
        }
    }

    private void doPrint(String html, String title) {
        final String jobName = getString(R.string.app_name) + (title == null || title.isEmpty() ? "" : " - " + title);
        printView = new WebView(this);
        printView.getSettings().setJavaScriptEnabled(false);
        printView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) {
                    toast("此设备不支持系统打印");
                    return;
                }
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(jobName);
                pm.print(jobName, adapter, new PrintAttributes.Builder().build());
            }
        });
        printView.loadDataWithBaseURL(BuildConfig.START_URL, html, "text/html", "UTF-8", null);
    }

    // ---------------------------------------------------------------- UI

    private LinearLayout buildErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        errorText = new TextView(this);
        errorText.setTextSize(16);
        errorText.setTextColor(Color.parseColor("#666666"));
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, dp(12), 0, dp(20));
        box.addView(errorText);

        Button retry = new Button(this);
        retry.setText("重新加载");
        retry.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            String cur = webView.getUrl();
            if (cur == null || !isAllowed(Uri.parse(cur))) webView.loadUrl(BuildConfig.START_URL);
            else webView.reload();
        });
        box.addView(retry);

        Button home = new Button(this);
        home.setText("返回首页");
        home.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            webView.loadUrl(BuildConfig.START_URL);
        });
        box.addView(home);
        return box;
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorView.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- 生命周期

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (errorView.getVisibility() == View.VISIBLE) {
            errorView.setVisibility(View.GONE);
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            toast("再按一次返回键退出");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_PERM && pendingPermission != null) {
            boolean ok = true;
            for (int r : grantResults) ok &= r == PackageManager.PERMISSION_GRANTED;
            if (ok) pendingPermission.grant(pendingPermission.getResources());
            else pendingPermission.deny();
            pendingPermission = null;
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
