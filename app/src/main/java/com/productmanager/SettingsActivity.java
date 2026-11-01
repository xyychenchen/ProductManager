package com.productmanager;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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

    /** 进度刷新节流：避免每条产品都跨线程刷一次 UI */
    private static final long PROGRESS_THROTTLE_MS = 120L;

    private MaterialToolbar toolbar;
    private TextView tvVersion;

    private ProductDatabase database;
    private ExecutorService executorService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private AlertDialog progressDialog;
    private long lastProgressUpdate;

    private List<Product> pendingExport;
    private ActivityResultLauncher<String> createDocumentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        database = ProductDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();

        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/html"),
                uri -> {
                    if (uri != null && pendingExport != null) {
                        writeHtml(uri, pendingExport);
                    }
                    pendingExport = null;
                });

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
        findViewById(R.id.card_about).setOnClickListener(v -> showAboutDialog());
        findViewById(R.id.card_author).setOnClickListener(v -> openGitHub());
        findViewById(R.id.card_export).setOnClickListener(v -> exportData());
    }

    private void loadVersionInfo() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.version_label, versionName));
        } catch (Exception e) {
            tvVersion.setText(getString(R.string.version_label, "1.0.0"));
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于项目")
                .setMessage("产品管理 APP\n\n" +
                        "一款专为直播带货设计的产品管理工具，帮助主播快速记录和管理产品信息。\n\n" +
                        "功能特点：\n" +
                        "• 产品信息管理（名称、规格、尺寸、材质等）\n" +
                        "• 产品照片拍摄与选择\n" +
                        "• 字母索引快速定位\n" +
                        "• 产品话术记录\n" +
                        "• 数据导出HTML表格（含嵌入图片）\n\n" +
                        "开发者：xyychenchen")
                .setPositiveButton("确定", null)
                .show();
    }

    private void openGitHub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xyychenchen")));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= 导出 =================

    /**
     * 导出流程：查询一次 → 让用户选保存位置 → 写入
     *
     * 原来查询了两次（一次判空、一次写文件），这里合并成一次并把结果传给下一步。
     */
    private void exportData() {
        showProgress(R.string.export_preparing);
        executorService.execute(() -> {
            List<Product> products = database.productDao().getAllProducts();
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                dismissProgress();
                if (products.isEmpty()) {
                    toast(R.string.no_data_to_export);
                    return;
                }
                String fileName = "products_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                        + ".html";
                pendingExport = products;
                createDocumentLauncher.launch(fileName);
            });
        });
    }

    private void writeHtml(Uri uri, List<Product> products) {
        showProgress(R.string.exporting);
        executorService.execute(() -> {
            int imageCount = 0;
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) {
                    throw new IllegalStateException("无法打开目标文件");
                }
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)))) {
                    imageCount = writeDocument(writer, products);
                }
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    dismissProgress();
                    toast(getString(R.string.export_failed, String.valueOf(e.getMessage())));
                });
                return;
            }

            final int images = imageCount;
            final int count = products.size();
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                dismissProgress();
                Toast.makeText(SettingsActivity.this,
                        "导出成功！\n共 " + count + " 条数据，" + images + " 张图片已嵌入\n\n可以用浏览器或Excel打开HTML文件查看",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    /**
     * 写整个 HTML 文档，返回成功嵌入的图片数量
     */
    private int writeDocument(PrintWriter writer, List<Product> products) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        int imageCount = 0;

        writer.println("<!DOCTYPE html>");
        writer.println("<html lang='zh-CN'>");
        writer.println("<head>");
        writer.println("<meta charset='UTF-8'>");
        writer.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        writer.println("<title>产品列表</title>");
        writer.println("<style>");
        writer.println("body { font-family: 'Microsoft YaHei', Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }");
        writer.println("h1 { text-align: center; color: #333; }");
        writer.println("table { border-collapse: collapse; width: 100%; background-color: white; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
        writer.println("th { background-color: #2196F3; color: white; padding: 12px 8px; text-align: center; font-weight: bold; }");
        writer.println("td { border: 1px solid #ddd; padding: 10px 8px; text-align: center; vertical-align: middle; }");
        writer.println("tr:nth-child(even) { background-color: #f9f9f9; }");
        writer.println("tr:hover { background-color: #f1f1f1; }");
        writer.println(".product-img { max-width: 100px; max-height: 100px; object-fit: contain; }");
        writer.println(".no-img { color: #999; font-style: italic; }");
        writer.println(".info { text-align: center; margin-top: 20px; color: #666; }");
        writer.println("</style>");
        writer.println("</head>");
        writer.println("<body>");
        writer.println("<h1>产品列表</h1>");
        writer.println("<table>");
        writer.println("<tr>");
        writer.println("<th>ID</th>");
        writer.println("<th>产品名称</th>");
        writer.println("<th>规格</th>");
        writer.println("<th>尺寸</th>");
        writer.println("<th>材质</th>");
        writer.println("<th>备注</th>");
        writer.println("<th>价格(美元)</th>");
        writer.println("<th>产品图片</th>");
        writer.println("<th>话术</th>");
        writer.println("<th>创建时间</th>");
        writer.println("<th>更新时间</th>");
        writer.println("</tr>");

        int total = products.size();
        for (int i = 0; i < total; i++) {
            Product product = products.get(i);
            if (product.getPhotoPath() != null && !product.getPhotoPath().isEmpty()) {
                imageCount += writeRow(writer, product, dateFormat) ? 1 : 0;
            } else {
                writeRow(writer, product, dateFormat);
            }
            publishProgress(i + 1, total);
        }

        writer.println("</table>");
        writer.println("<p class='info'>导出时间: " + dateFormat.format(new Date())
                + " | 共 " + total + " 条数据，" + imageCount + " 张图片</p>");
        writer.println("</body>");
        writer.println("</html>");
        writer.flush();
        return imageCount;
    }

    /**
     * 写一行数据，返回是否成功嵌入了图片
     */
    private boolean writeRow(PrintWriter writer, Product product, SimpleDateFormat dateFormat) {
        boolean imageEmbedded = false;

        writer.print("<tr>");
        writer.print("<td>" + product.getId() + "</td>");
        writer.print("<td>" + escapeHtml(product.getName()) + "</td>");
        writer.print("<td>" + escapeHtml(product.getSpecification()) + "</td>");
        writer.print("<td>" + escapeHtml(product.getSize()) + "</td>");
        writer.print("<td>" + escapeHtml(product.getMaterial()) + "</td>");
        writer.print("<td>" + escapeHtml(product.getRemark()) + "</td>");
        writer.print("<td>" + String.format(Locale.US, "$%.2f", product.getPrice()) + "</td>");

        writer.print("<td>");
        String photoPath = product.getPhotoPath();
        if (photoPath != null && !photoPath.isEmpty() && new File(photoPath).exists()) {
            String base64Image = imageToBase64(photoPath);
            if (base64Image != null) {
                writer.print("<img src='data:image/jpeg;base64," + base64Image
                        + "' class='product-img' alt='产品图片'>");
                imageEmbedded = true;
            } else {
                writer.print("<span class='no-img'>图片加载失败</span>");
            }
        } else {
            writer.print("<span class='no-img'>无图片</span>");
        }
        writer.print("</td>");

        writer.print("<td>" + escapeHtml(product.getScript()) + "</td>");
        writer.print("<td>" + (product.getCreateTime() > 0
                ? dateFormat.format(new Date(product.getCreateTime())) : "") + "</td>");
        writer.print("<td>" + (product.getUpdateTime() > 0
                ? dateFormat.format(new Date(product.getUpdateTime())) : "") + "</td>");
        writer.println("</tr>");
        return imageEmbedded;
    }

    /**
     * 进度提示做节流，1000 条数据也不会产生 1000 次 UI 更新
     */
    private void publishProgress(int current, int total) {
        long now = System.currentTimeMillis();
        if (current != total && now - lastProgressUpdate < PROGRESS_THROTTLE_MS) {
            return;
        }
        lastProgressUpdate = now;
        handler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()
                    && !isFinishing() && !isDestroyed()) {
                progressDialog.setMessage(getString(R.string.export_progress, current, total));
            }
        });
    }

    // ================= 工具方法 =================

    private void showProgress(@StringRes int messageRes) {
        dismissProgress();
        progressDialog = new AlertDialog.Builder(this)
                .setMessage(messageRes)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            progressDialog = null;
        }
    }

    private void toast(@StringRes int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 将图片转换为Base64字符串（缩放到 200px 以内，避免导出文件过大）
     */
    private String imageToBase64(String imagePath) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, bounds);

        int maxSize = 200;
        int scale = 1;
        if (bounds.outWidth > maxSize || bounds.outHeight > maxSize) {
            int widthScale = Math.round((float) bounds.outWidth / maxSize);
            int heightScale = Math.round((float) bounds.outHeight / maxSize);
            scale = Math.max(widthScale, heightScale);
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = scale;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, decodeOptions);
        if (bitmap == null) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * 转义HTML特殊字符
     */
    private String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br>");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissProgress();
        handler.removeCallbacksAndMessages(null);
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
