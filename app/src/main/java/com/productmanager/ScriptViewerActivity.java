package com.productmanager;

import android.os.Bundle;
import android.text.Spanned;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;

/**
 * 话术全屏查看页面
 * 支持Markdown格式渲染
 */
public class ScriptViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_NAME = "product_name";
    public static final String EXTRA_SCRIPT = "script";

    private MaterialToolbar toolbar;
    private TextView tvProductName;
    private TextView tvScriptContent;

    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_viewer);

        // 初始化Markwon（Markdown渲染器）
        markwon = Markwon.builder(this)
                .usePlugin(CorePlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(this))
                .build();

        initViews();
        setupToolbar();
        loadScript();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvProductName = findViewById(R.id.tv_product_name);
        tvScriptContent = findViewById(R.id.tv_script_content);
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
        String script = getIntent().getStringExtra(EXTRA_SCRIPT);

        // 显示产品名称
        if (productName != null && !productName.isEmpty()) {
            tvProductName.setText("产品：" + productName);
        } else {
            tvProductName.setText("产品话术");
        }

        // 渲染Markdown内容
        if (script != null && !script.isEmpty()) {
            // 使用Markwon渲染Markdown
            Spanned markdown = markwon.toMarkdown(script);
            markwon.setParsedMarkdown(tvScriptContent, markdown);
        } else {
            tvScriptContent.setText("暂无话术内容");
            tvScriptContent.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }
}
