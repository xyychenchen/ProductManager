package com.productmanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * 设置页面
 */
public class SettingsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupToolbar();
        setupClickListeners();
        loadVersionInfo();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvVersion = findViewById(R.id.tv_version);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        // 关于项目
        findViewById(R.id.card_about).setOnClickListener(v -> showAboutDialog());

        // 作者 GitHub
        findViewById(R.id.card_author).setOnClickListener(v -> openGitHub());
    }

    private void loadVersionInfo() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText("版本 " + versionName);
        } catch (Exception e) {
            tvVersion.setText("版本 1.0.0");
        }
    }

    private void showAboutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("关于项目")
                .setMessage("产品管理 APP\n\n" +
                        "一款专为直播带货设计的产品管理工具，帮助主播快速记录和管理产品信息。\n\n" +
                        "功能特点：\n" +
                        "• 产品信息管理（名称、规格、尺寸、材质等）\n" +
                        "• 产品照片拍摄与选择\n" +
                        "• 字母索引快速定位\n" +
                        "• 产品话术记录\n\n" +
                        "开发者：xyychenchen")
                .setPositiveButton("确定", null)
                .show();
    }

    private void openGitHub() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xyychenchen"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }
}
