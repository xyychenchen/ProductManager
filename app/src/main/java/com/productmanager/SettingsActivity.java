package com.productmanager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
                        "• 数据导出HTML表格（含嵌入图片）\n\n" +
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
     * 导出数据到HTML表格（包含嵌入图片）
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
                String fileName = "products_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".html";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/html");
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
     * 将数据导出到指定URI（HTML表格格式，包含嵌入图片）
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

                PrintWriter writer = new PrintWriter(outputStream, true);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                int imageCount = 0;

                // 写入HTML头部
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

                // 写入数据行
                for (int i = 0; i < products.size(); i++) {
                    Product product = products.get(i);
                    StringBuilder row = new StringBuilder();
                    row.append("<tr>");

                    // ID
                    row.append("<td>").append(product.getId()).append("</td>");

                    // 产品名称
                    row.append("<td>").append(escapeHtml(product.getName())).append("</td>");

                    // 规格
                    row.append("<td>").append(escapeHtml(product.getSpecification())).append("</td>");

                    // 尺寸
                    row.append("<td>").append(escapeHtml(product.getSize())).append("</td>");

                    // 材质
                    row.append("<td>").append(escapeHtml(product.getMaterial())).append("</td>");

                    // 备注
                    row.append("<td>").append(escapeHtml(product.getRemark())).append("</td>");

                    // 价格
                    row.append("<td>").append(String.format(Locale.US, "$%.2f", product.getPrice())).append("</td>");

                    // 图片
                    String photoPath = product.getPhotoPath();
                    row.append("<td>");
                    if (photoPath != null && !photoPath.isEmpty()) {
                        File imageFile = new File(photoPath);
                        if (imageFile.exists()) {
                            try {
                                String base64Image = imageToBase64(photoPath);
                                if (base64Image != null) {
                                    row.append("<img src='data:image/jpeg;base64,").append(base64Image).append("' class='product-img' alt='产品图片'>");
                                    imageCount++;
                                } else {
                                    row.append("<span class='no-img'>无图片</span>");
                                }
                            } catch (Exception e) {
                                row.append("<span class='no-img'>图片加载失败</span>");
                            }
                        } else {
                            row.append("<span class='no-img'>无图片</span>");
                        }
                    } else {
                        row.append("<span class='no-img'>无图片</span>");
                    }
                    row.append("</td>");

                    // 话术
                    row.append("<td>").append(escapeHtml(product.getScript())).append("</td>");

                    // 创建时间
                    row.append("<td>").append(product.getCreateTime() > 0 ? dateFormat.format(new Date(product.getCreateTime())) : "").append("</td>");

                    // 更新时间
                    row.append("<td>").append(product.getUpdateTime() > 0 ? dateFormat.format(new Date(product.getUpdateTime())) : "").append("</td>");

                    row.append("</tr>");

                    writer.println(row.toString());

                    // 更新进度
                    final int progress = i + 1;
                    final int total = products.size();
                    runOnUiThread(() -> {
                        progressDialog.setMessage("正在导出... " + progress + "/" + total);
                    });
                }

                // 写入HTML尾部
                writer.println("</table>");
                writer.println("<p class='info'>导出时间: " + dateFormat.format(new Date()) + " | 共 " + products.size() + " 条数据，" + imageCount + " 张图片</p>");
                writer.println("</body>");
                writer.println("</html>");

                writer.flush();
                writer.close();
                outputStream.close();

                final int finalImageCount = imageCount;
                final int productCount = products.size();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                        "导出成功！\n共 " + productCount + " 条数据，" + finalImageCount + " 张图片已嵌入\n\n可以用浏览器或Excel打开HTML文件查看",
                        Toast.LENGTH_LONG).show();
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
     * 将图片转换为Base64字符串
     */
    private String imageToBase64(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                return null;
            }

            // 读取图片并压缩
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            // 计算缩放比例
            int maxSize = 200; // 最大宽高
            int scale = 1;
            if (options.outWidth > maxSize || options.outHeight > maxSize) {
                int widthScale = Math.round((float) options.outWidth / maxSize);
                int heightScale = Math.round((float) options.outHeight / maxSize);
                scale = Math.max(widthScale, heightScale);
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = scale;
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, decodeOptions);

            if (bitmap == null) {
                return null;
            }

            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] imageBytes = baos.toByteArray();
            bitmap.recycle();

            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
