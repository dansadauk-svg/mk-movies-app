package ng.com.mkmovies.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {
    private static final String TAG = "MKMoviesMain";
    private static final int REQ_POST_NOTIFICATIONS = 701;
    private static final int REQ_FILE_CHOOSER = 702;
    private static final String HOME_URL = "https://mkmovies.com.ng/?source=android_app";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> filePathCallback;
    private ProgressBar topProgressBar;
    private ProgressBar splashProgressBar;
    private FrameLayout splashOverlay;
    private boolean firstPageLoaded = false;
    private boolean offlineToastShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        createNotificationChannel();
        setupWebView();
        requestNotificationPermissionIfNeeded();
        registerNativeToken();

        String url = getLaunchUrl();
        webView.loadUrl(url);
    }

    private String getLaunchUrl() {
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && ("https".equals(data.getScheme()) || "http".equals(data.getScheme()))) {
                return data.toString();
            }
            String extra = intent.getStringExtra("url");
            if (extra != null && extra.startsWith("http")) return extra;
            extra = intent.getStringExtra("open_url");
            if (extra != null && extra.startsWith("http")) return extra;
            extra = intent.getStringExtra("gcm.notification.link");
            if (extra != null && extra.startsWith("http")) return extra;
        }
        return HOME_URL;
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applySafeAreaForCameraCutout(View root) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                view.setPadding(left, top, right, bottom);
                return insets;
            });
            root.post(root::requestApplyInsets);
        }
    }

    private void setupWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        applySafeAreaForCameraCutout(root);

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        webView = new WebView(this);
        swipeRefreshLayout.addView(webView, new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(swipeRefreshLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        topProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topProgressBar.setMax(100);
        topProgressBar.setProgress(0);
        topProgressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        topParams.gravity = Gravity.TOP;
        root.addView(topProgressBar, topParams);

        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams splashParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        LinearLayout splashContent = new LinearLayout(this);
        splashContent.setOrientation(LinearLayout.VERTICAL);
        splashContent.setGravity(Gravity.CENTER);
        splashContent.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_splash);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        logoParams.width = dp(280);
        logoParams.bottomMargin = dp(28);
        splashContent.addView(logo, logoParams);

        splashProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        splashProgressBar.setMax(100);
        splashProgressBar.setProgress(8);
        splashProgressBar.setIndeterminate(false);
        LinearLayout.LayoutParams splashBarParams = new LinearLayout.LayoutParams(
                dp(220),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        splashContent.addView(splashProgressBar, splashBarParams);

        FrameLayout.LayoutParams splashContentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        splashOverlay.addView(splashContent, splashContentParams);
        root.addView(splashOverlay, splashParams);

        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        updateCacheMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua + " MKMoviesAndroid/" + BuildConfig.VERSION_NAME);

        webView.setBackgroundColor(Color.WHITE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                    return handleUrl(request.getUrl());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                updateCacheMode();
                topProgressBar.setVisibility(View.VISIBLE);
                if (!firstPageLoaded) {
                    splashOverlay.setVisibility(View.VISIBLE);
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
                maybeHideSplash();
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                topProgressBar.setProgress(newProgress);
                splashProgressBar.setProgress(Math.max(8, newProgress));
                if (newProgress >= 100) {
                    topProgressBar.setVisibility(View.GONE);
                    maybeHideSplash();
                } else {
                    topProgressBar.setVisibility(View.VISIBLE);
                }
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                startDownload(url, userAgent, contentDisposition, mimeType);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
    }

    private void maybeHideSplash() {
        if (webView.getProgress() >= 100) {
            firstPageLoaded = true;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                splashOverlay.setVisibility(View.GONE);
                topProgressBar.setVisibility(View.GONE);
            }, 180);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private void updateCacheMode() {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        if (isNetworkAvailable()) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            offlineToastShown = false;
        } else {
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            if (!offlineToastShown) {
                offlineToastShown = true;
                Toast.makeText(this, "Offline mode: loading saved pages", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null) return false;

        if (("http".equals(scheme) || "https".equals(scheme))) {
            if (host != null && (host.equals("mkmovies.com.ng") || host.endsWith(".mkmovies.com.ng"))) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            request.setTitle(filename);
            request.setDescription("Downloading from MK Movies");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            registerNativeToken();
        }
    }

    private void registerNativeToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                MkTokenRegistrar.registerTokenAsync(task.getResult());
                Log.d(TAG, "Native FCM token registered");
            } else {
                Log.w(TAG, "Could not get native FCM token", task.getException());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    "mk_movies_channel",
                    "MK Movies Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("New movies, new episodes and account alerts from MK Movies.");
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null) webView.loadUrl(getLaunchUrl());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCacheMode();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }
}
