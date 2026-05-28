package com.productmanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
 * 话术全屏编辑/查看页面
 * 支持Markdown格式渲染
 */
public class ScriptViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_NAME = "product_name";
    public static final String EXTRA_SCRIPT = "script";
    public static final String EXTRA_SCRIPT_RESULT = "script_result";

    private MaterialToolbar toolbar;
    private TextView tvProductName;
    private TextView tvSave;
    private TextView tvTabEdit;
    private TextView tvTabPreview;
    private EditText etScriptEdit;
    private ScrollView scrollPreview;
    private TextView tvScriptPreview;

    private Markwon markwon;
    private boolean isEditMode = true;

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
        setupTabs();
        loadScript();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvProductName = findViewById(R.id.tv_product_name);
        tvSave = findViewById(R.id.tv_save);
        tvTabEdit = findViewById(R.id.tv_tab_edit);
        tvTabPreview = findViewById(R.id.tv_tab_preview);
        etScriptEdit = findViewById(R.id.et_script_edit);
        scrollPreview = findViewById(R.id.scroll_preview);
        tvScriptPreview = findViewById(R.id.tv_script_preview);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> {
            // 返回时不保存，直接关闭
            finish();
        });

        // 保存按钮
        tvSave.setOnClickListener(v -> saveAndFinish());
    }

    private void setupTabs() {
        tvTabEdit.setOnClickListener(v -> {
            if (!isEditMode) {
                isEditMode = true;
                updateTabStyles();
                etScriptEdit.setVisibility(View.VISIBLE);
                scrollPreview.setVisibility(View.GONE);
            }
        });

        tvTabPreview.setOnClickListener(v -> {
            if (isEditMode) {
                isEditMode = false;
                updateTabStyles();
                etScriptEdit.setVisibility(View.GONE);
                scrollPreview.setVisibility(View.VISIBLE);
                updatePreview();
            }
        });
    }

    private void updateTabStyles() {
        if (isEditMode) {
            tvTabEdit.setBackgroundResource(R.drawable.bg_tab_selected);
            tvTabEdit.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            tvTabPreview.setBackgroundResource(R.drawable.bg_tab_normal);
            tvTabPreview.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            tvTabEdit.setBackgroundResource(R.drawable.bg_tab_normal);
            tvTabEdit.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvTabPreview.setBackgroundResource(R.drawable.bg_tab_selected);
            tvTabPreview.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        }
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

        // 显示话术内容到编辑框
        if (script != null) {
            etScriptEdit.setText(script);
        }
    }

    private void updatePreview() {
        String script = etScriptEdit.getText().toString();
        if (script != null && !script.isEmpty()) {
            Spanned markdown = markwon.toMarkdown(script);
            markwon.setParsedMarkdown(tvScriptPreview, markdown);
        } else {
            tvScriptPreview.setText("暂无话术内容");
            tvScriptPreview.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void saveAndFinish() {
        String script = etScriptEdit.getText().toString();

        // 返回结果给调用页面
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SCRIPT_RESULT, script);
        setResult(RESULT_OK, resultIntent);

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onBackPressed() {
        // 按返回键不保存，直接关闭
        super.onBackPressed();
    }
}
