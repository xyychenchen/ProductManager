package com.productmanager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 设置页面
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_EXPORT = 100;

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
        findViewById(R.id.card_export).setOnClickListener(v -> exportData());
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
                        "• 数据导出（含图片）\n\n" +
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
     * 导出数据（包含图片的ZIP压缩包）
     */
    private void exportData() {
        // 显示加载对话框
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在准备...");
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
                String fileName = "products_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".zip";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(intent, REQUEST_EXPORT);
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                exportDataToUri(uri);
            }
        }
    }

    /**
     * 将数据导出到指定URI（ZIP格式，包含CSV和图片）
     */
    private void exportDataToUri(Uri uri) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在导出...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        executorService.execute(() -> {
            try {
                List<Product> products = database.productDao().getAllProducts();

                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    throw new Exception("无法打开文件");
                }

                ZipOutputStream zipOut = new ZipOutputStream(outputStream);

                // 创建CSV内容
                StringBuilder csvContent = new StringBuilder();
                csvContent.append("ID,产品名称,规格,尺寸,材质,备注,价格(美元),图片文件,话术,创建时间,更新时间\n");

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Map<String, String> imageMap = new HashMap<>(); // 原始路径 -> 新文件名映射
                int imageIndex = 1;

                // 第一遍：收集图片信息
                for (Product product : products) {
                    String photoPath = product.getPhotoPath();
                    if (photoPath != null && !photoPath.isEmpty()) {
                        File imageFile = new File(photoPath);
                        if (imageFile.exists()) {
                            String extension = "";
                            int dotIndex = photoPath.lastIndexOf('.');
                            if (dotIndex > 0) {
                                extension = photoPath.substring(dotIndex);
                            }
                            String newFileName = "image_" + product.getId() + extension;
                            imageMap.put(photoPath, newFileName);
                        }
                    }
                }

                // 第二遍：生成CSV内容
                for (Product product : products) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCSV(String.valueOf(product.getId()))).append(",");
                    line.append(escapeCSV(product.getName())).append(",");
                    line.append(escapeCSV(product.getSpecification())).append(",");
                    line.append(escapeCSV(product.getSize())).append(",");
                    line.append(escapeCSV(product.getMaterial())).append(",");
                    line.append(escapeCSV(product.getRemark())).append(",");
                    line.append(String.format(Locale.US, "%.2f", product.getPrice())).append(",");

                    // 图片文件名
                    String photoPath = product.getPhotoPath();
                    String imageFileName = "";
                    if (photoPath != null && !photoPath.isEmpty()) {
                        imageFileName = imageMap.get(photoPath);
                        if (imageFileName == null) {
                            imageFileName = "";
                        }
                    }
                    line.append(escapeCSV(imageFileName)).append(",");

                    line.append(escapeCSV(product.getScript())).append(",");
                    line.append(escapeCSV(product.getCreateTime() > 0 ? dateFormat.format(new Date(product.getCreateTime())) : "")).append(",");
                    line.append(escapeCSV(product.getUpdateTime() > 0 ? dateFormat.format(new Date(product.getUpdateTime())) : ""));
                    csvContent.append(line.toString()).append("\n");
                }

                // 写入CSV文件到ZIP
                ZipEntry csvEntry = new ZipEntry("products.csv");
                zipOut.putNextEntry(csvEntry);
                zipOut.write(csvContent.toString().getBytes("UTF-8"));
                zipOut.closeEntry();

                // 写入图片文件到ZIP
                int imageCount = 0;
                for (Map.Entry<String, String> entry : imageMap.entrySet()) {
                    String originalPath = entry.getKey();
                    String newFileName = entry.getValue();
                    File imageFile = new File(originalPath);

                    if (imageFile.exists()) {
                        ZipEntry imageEntry = new ZipEntry("images/" + newFileName);
                        zipOut.putNextEntry(imageEntry);

                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(imageFile));
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = bis.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, count);
                        }
                        bis.close();
                        zipOut.closeEntry();
                        imageCount++;
                    }
                }

                zipOut.flush();
                zipOut.close();
                outputStream.close();

                final int finalImageCount = imageCount;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    String message = "导出成功！\n共 " + products.size() + " 条数据，" + finalImageCount + " 张图片\n\nZIP文件包含：\n• products.csv（产品数据）\n• images/（产品图片）";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
