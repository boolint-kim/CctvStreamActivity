package com.boolint.camlocation;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.boolint.camlocation.helper.CctvApiHelper;

public class CctvStreamActivity extends AppCompatActivity {

    private static final String TAG = "ttt";

    private WebView webView;
    private LinearLayout loadingOverlay;
    private ProgressBar loading;
    private TextView statusText;

    private CctvApiHelper apiHelper;
    private boolean isFirstLoad = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cctv_stream);

        // Views
        webView = findViewById(R.id.webview);
        loadingOverlay = findViewById(R.id.loading_overlay);
        loading = findViewById(R.id.loading);
        statusText = findViewById(R.id.status_text);

        apiHelper = new CctvApiHelper();

        String cctvId = getIntent().getStringExtra("cctvId");
        cctvId = MainData.mCurrentCctvItemVo.roadSectionId;

        if (cctvId == null) {
            Toast.makeText(this, "CCTV ID가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "🚀 CCTV 로드: " + cctvId);

        setupWebView();
        applyFullScreen();
        loadCctv(cctvId);
    }

    // ============================================================================
    // CCTV API
    // ============================================================================

    private void loadCctv(String cctvId) {
        showLoading("로딩 중...");

        apiHelper.getCctvInfo(cctvId, new CctvApiHelper.CctvResponseListener() {
            @Override
            public void onSuccess(CctvApiHelper.CctvInfo cctvInfo) {
                runOnUiThread(() -> playWithWebView(cctvInfo.getStreamPageUrl()));
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    hideLoading();
                    statusText.setText("오류: " + error);
                    loadingOverlay.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    // ============================================================================
    // Immersive
    // ============================================================================

    private void applyFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    // ============================================================================
    // WebView
    // ============================================================================

    private void setupWebView() {
        WebSettings ws = webView.getSettings();

        // JavaScript & DOM
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        // 🔥 캐시 & 버퍼링 최적화
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setDatabaseEnabled(true);

        // 🔥 하드웨어 가속 (가장 중요!)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 🔥 렌더링 우선순위 (API 33부터 deprecated)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
        }

        // 이미지 로딩
        ws.setLoadsImagesAutomatically(true);
        ws.setBlockNetworkImage(false);

        // Mixed Content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 줌
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);

        // UI
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(0xFF000000);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return new View(CctvStreamActivity.this);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {}

            @Override
            public void onHideCustomView() {}

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress > 10) {
                    injectAllScripts(view);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                injectBaseCSSImmediately(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectAllScripts(view);
                view.postDelayed(() -> injectAllScripts(view), 500);
                view.postDelayed(() -> {
                    injectAllScripts(view);
                    hideLoading();
                }, 1000);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }
        });
    }

    // ============================================================================
    // JS Inject (개선된 버전)
    // ============================================================================

    /** 🔥 페이지 시작과 동시에 기본 CSS 주입 (깜박임 최소화) */
    private void injectBaseCSSImmediately(WebView view) {
        String js =
                "javascript:(function(){ " +
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "body{margin:0;padding:0;background:#000!important;overflow:hidden!important;}" +
                        "video{" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:50%!important;left:50%!important;" +
                        "transform:translate(-50%,-50%)!important;" +
                        "pointer-events:none!important;" +
                        "}" +
                        "video::-webkit-media-controls{display:none!important;}" +
                        "video::-webkit-media-controls-panel{display:none!important;}" +
                        "video::-webkit-media-controls-play-button{display:none!important;}" +
                        "video::-webkit-media-controls-start-playback-button{display:none!important;}" +
                        "`;" +
                        "document.head.appendChild(s);" +
                        "}})();";

        view.evaluateJavascript(js, null);
    }

    /** 🔥 모든 스크립트 (버퍼링 최적화 포함) */
    private void injectAllScripts(WebView view) {
        String js =
                "javascript:(function(){ " +
                        // CSS
                        "if(!document.getElementById('cctv-base-style')){ " +
                        "var s=document.createElement('style');" +
                        "s.id='cctv-base-style';" +
                        "s.innerHTML=`" +
                        "body{margin:0;padding:0;background:#000!important;overflow:hidden!important;}" +
                        "video{" +
                        "width:100vw!important;" +
                        "height:100vh!important;" +
                        "object-fit:contain!important;" +
                        "background:black!important;" +
                        "position:fixed!important;" +
                        "top:50%!important;left:50%!important;" +
                        "transform:translate(-50%,-50%)!important;" +
                        "pointer-events:none!important;" +
                        "}" +
                        "video::-webkit-media-controls{display:none!important;}" +
                        "video::-webkit-media-controls-panel{display:none!important;}" +
                        "video::-webkit-media-controls-play-button{display:none!important;}" +
                        "video::-webkit-media-controls-start-playback-button{display:none!important;}" +
                        "`;" +
                        "document.head.appendChild(s);" +
                        "}" +

                        // Video 설정
                        "document.querySelectorAll('video').forEach(function(v){ " +
                        "v.removeAttribute('controls');" +
                        "v.removeAttribute('poster');" +
                        "v.poster='';" +
                        "v.autoplay=true;" +
                        "v.muted=true;" +
                        "v.playsInline=true;" +
                        "v.webkitPlaysInline=true;" +
                        "v.style.pointerEvents='none';" +

                        // 🔥 버퍼링 최적화
                        "v.preload='auto';" +
                        "v.setAttribute('preload', 'auto');" +

                        // 🔥 끊김 대응 이벤트
                        "if(!v.hasBufferingListeners){" +
                        "v.hasBufferingListeners=true;" +

                        "v.addEventListener('stalled', function(){" +
                        "setTimeout(function(){if(v.paused)v.play().catch(function(){});}, 1000);" +
                        "});" +

                        "v.addEventListener('suspend', function(){" +
                        "if(v.paused)v.play().catch(function(){});" +
                        "});" +

                        "}" +

                        // 재생
                        "if(v.paused && v.readyState >= 2){" +
                        "v.play().catch(function(){});" +
                        "}" +
                        "});" +
                        "})();";

        view.evaluateJavascript(js, null);
    }


    // ============================================================================
    // WebView Load
    // ============================================================================

    private void playWithWebView(String streamPageUrl) {
        Log.d(TAG, "🌐 WebView 재생: " + streamPageUrl);

        // 🔥 재로드 시 WebView 초기화
        if (!isFirstLoad) {
            webView.clearCache(true);
            webView.clearHistory();
        }
        isFirstLoad = false;

        webView.loadUrl(streamPageUrl);
        webView.setVisibility(View.VISIBLE);
    }

    // ============================================================================
    // Loading UI
    // ============================================================================

    private void showLoading(String message) {
        loadingOverlay.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }

    // ============================================================================
    // Lifecycle
    // ============================================================================

    @Override
    protected void onResume() {
        super.onResume();
        applyFullScreen();
        if (webView != null) {
            webView.onResume();
            // 🔥 화면 복귀 시 스크립트 재적용
            webView.postDelayed(() -> injectAllScripts(webView), 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
