package com.productmanager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设置页面
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_EXPORT_CSV = 100;

    private MaterialToolbar toolbar;
    private TextView tvVersion;

    private ProductDatabase database;
    private ExecutorService executorService;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化数据库
        database = ProductDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();

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

        // 导出数据
        findViewById(R.id.card_export).setOnClickListener(v -> exportToCSV());
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
                        "• 产品话术记录\n" +
                        "• 数据导出CSV\n\n" +
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

    /**
     * 导出数据到CSV
     */
    private void exportToCSV() {
        // 显示加载对话框
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在导出...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 在后台线程获取产品数据
        executorService.execute(() -> {
            List<Product> products = database.productDao().getAllProducts();

            runOnUiThread(() -> {
                progressDialog.dismiss();

                if (products.isEmpty()) {
                    Toast.makeText(this, "没有产品数据可导出", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 使用 Storage Access Framework 让用户选择保存位置
                String fileName = "products_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".csv";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(intent, REQUEST_EXPORT_CSV);
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXPORT_CSV && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                exportDataToUri(uri);
            }
        }
    }

    /**
     * 将数据导出到指定URI
     */
    private void exportDataToUri(Uri uri) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在保存...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        executorService.execute(() -> {
            try {
                List<Product> products = database.productDao().getAllProducts();

                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    throw new Exception("无法打开文件");
                }

                PrintWriter writer = new PrintWriter(outputStream);

                // 写入CSV表头
                writer.println("ID,产品名称,规格,尺寸,材质,备注,价格(美元),话术,创建时间,更新时间");

                // 写入数据
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (Product product : products) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCSV(String.valueOf(product.getId()))).append(",");
                    line.append(escapeCSV(product.getName())).append(",");
                    line.append(escapeCSV(product.getSpecification())).append(",");
                    line.append(escapeCSV(product.getSize())).append(",");
                    line.append(escapeCSV(product.getMaterial())).append(",");
                    line.append(escapeCSV(product.getRemark())).append(",");
                    line.append(String.format(Locale.US, "%.2f", product.getPrice())).append(",");
                    line.append(escapeCSV(product.getScript())).append(",");
                    line.append(escapeCSV(product.getCreateTime() > 0 ? dateFormat.format(new Date(product.getCreateTime())) : "")).append(",");
                    line.append(escapeCSV(product.getUpdateTime() > 0 ? dateFormat.format(new Date(product.getUpdateTime())) : ""));
                    writer.println(line.toString());
                }

                writer.flush();
                writer.close();
                outputStream.close();

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "导出成功！共 " + products.size() + " 条数据", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 转义CSV字段（处理逗号、引号、换行符）
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // 如果包含逗号、引号或换行符，需要用引号包围并转义内部引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
