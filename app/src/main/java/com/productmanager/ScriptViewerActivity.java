package com.productmanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;

/**
 * Markdown预览页面
 * 只用于查看渲染后的效果，不支持编辑
 */
public class ScriptViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";
    public static final String EXTRA_PRODUCT_NAME = "product_name";
    /** 编辑页尚未保存的草稿；为空时按 productId 从数据库读取 */
    public static final String EXTRA_SCRIPT_DRAFT = "script_draft";

    private MaterialToolbar toolbar;
    private TextView tvProductName;
    private TextView tvScriptPreview;

    private Markwon markwon;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_viewer);

        markwon = MarkwonHolder.get(this);
        executorService = Executors.newSingleThreadExecutor();

        initViews();
        setupToolbar();
        loadScript();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvProductName = findViewById(R.id.tv_product_name);
        tvScriptPreview = findViewById(R.id.tv_script_preview);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadScript() {
        String productName = getIntent().getStringExtra(EXTRA_PRODUCT_NAME);
        String draft = getIntent().getStringExtra(EXTRA_SCRIPT_DRAFT);
        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);

        if (!TextUtils.isEmpty(productName)) {
            tvProductName.setText(getString(R.string.script_of_product, productName));
        } else {
            tvProductName.setText(R.string.script_title);
        }

        if (!TextUtils.isEmpty(draft)) {
            render(draft);
            return;
        }
        if (productId == -1) {
            showEmpty();
            return;
        }

        // 从数据库取完整话术：避免把长文本塞进 Intent
        executorService.execute(() -> {
            Product product = ProductDatabase.getDatabase(getApplicationContext())
                    .productDao()
                    .getProductById(productId);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (product == null || TextUtils.isEmpty(product.getScript())) {
                    showEmpty();
                } else {
                    render(product.getScript());
                }
            });
        });
    }

    /**
     * Markdown 解析放在后台线程，长话术不阻塞首帧
     */
    private void render(final String script) {
        executorService.execute(() -> {
            final Spanned parsed = markwon.toMarkdown(script);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                markwon.setParsedMarkdown(tvScriptPreview, parsed);
            });
        });
    }

    private void showEmpty() {
        tvScriptPreview.setText(R.string.script_empty);
        tvScriptPreview.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executorService.shutdown();
    }
}
