package com.fp.foodorderingsystem.activities.customer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.config.PayMongoConfig;

/**
 * Hosts the PayMongo checkout inside the app so we can intercept the return URL
 * and automatically resume the checkout flow.
 */
public class PaymentWebViewActivity extends AppCompatActivity {

    public static final String EXTRA_CHECKOUT_URL = "checkout_url";
    public static final String EXTRA_PAYMENT_METHOD = "payment_method";
    public static final String EXTRA_PAYMENT_COMPLETED = "payment_completed";

    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_webview);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        TextView tvTitle = findViewById(R.id.tvTitle);
        ImageButton btnClose = findViewById(R.id.btnClose);

        String checkoutUrl = getIntent().getStringExtra(EXTRA_CHECKOUT_URL);
        String paymentMethod = getIntent().getStringExtra(EXTRA_PAYMENT_METHOD);

        if (TextUtils.isEmpty(checkoutUrl)) {
            finishWithResult(false);
            return;
        }

        tvTitle.setText(paymentMethod != null ? paymentMethod : "Payment");
        btnClose.setOnClickListener(v -> finishWithResult(false));

        configureWebView();
        webView.loadUrl(checkoutUrl);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;

        String url = uri.toString();
        if (url.startsWith(PayMongoConfig.PAYMENT_RETURN_URL)
                || url.startsWith(PayMongoConfig.APP_DEEP_LINK_SCHEME)) {
            // PayMongo finished the flow, return to checkout activity.
            finishWithResult(true);
            return true;
        }

        return false;
    }

    private void finishWithResult(boolean completed) {
        Intent data = new Intent();
        data.putExtra(EXTRA_PAYMENT_COMPLETED, completed);
        setResult(completed ? RESULT_OK : RESULT_CANCELED, data);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finishWithResult(false);
        }
    }
}

