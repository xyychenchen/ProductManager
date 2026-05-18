package com.productmanager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
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
                        "• 数据导出Excel（含嵌入图片）\n\n" +
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
     * 导出数据到Excel（包含嵌入图片）
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
                String fileName = "products_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".xlsx";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
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
     * 将数据导出到指定URI（Excel格式，包含嵌入图片）
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

                // 创建Excel工作簿
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("产品列表");

                // 创建样式
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 12);
                headerStyle.setFont(headerFont);

                CellStyle cellStyle = workbook.createCellStyle();
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                cellStyle.setWrapText(true);

                // 创建表头
                Row headerRow = sheet.createRow(0);
                String[] headers = {"ID", "产品名称", "规格", "尺寸", "材质", "备注", "价格(美元)", "产品图片", "话术", "创建时间", "更新时间"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // 设置列宽
                sheet.setColumnWidth(0, 2000);   // ID
                sheet.setColumnWidth(1, 8000);   // 产品名称
                sheet.setColumnWidth(2, 6000);   // 规格
                sheet.setColumnWidth(3, 4000);   // 尺寸
                sheet.setColumnWidth(4, 4000);   // 材质
                sheet.setColumnWidth(5, 6000);   // 备注
                sheet.setColumnWidth(6, 3000);   // 价格
                sheet.setColumnWidth(7, 5000);   // 图片
                sheet.setColumnWidth(8, 10000);  // 话术
                sheet.setColumnWidth(9, 5000);   // 创建时间
                sheet.setColumnWidth(10, 5000);  // 更新时间

                // 设置表头行高
                headerRow.setHeightInPoints(25);

                // 创建绘图对象用于添加图片
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                CreationHelper helper = workbook.getCreationHelper();

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                int imageCount = 0;

                // 写入数据行
                for (int i = 0; i < products.size(); i++) {
                    Product product = products.get(i);
                    Row row = sheet.createRow(i + 1);

                    // 设置行高（如果有图片则设置更高的行）
                    String photoPath = product.getPhotoPath();
                    boolean hasImage = photoPath != null && !photoPath.isEmpty() && new File(photoPath).exists();
                    if (hasImage) {
                        row.setHeightInPoints(80); // 图片行高度
                    } else {
                        row.setHeightInPoints(30); // 普通行高度
                    }

                    // ID
                    Cell cell0 = row.createCell(0);
                    cell0.setCellValue(product.getId());
                    cell0.setCellStyle(cellStyle);

                    // 产品名称
                    Cell cell1 = row.createCell(1);
                    cell1.setCellValue(product.getName() != null ? product.getName() : "");
                    cell1.setCellStyle(cellStyle);

                    // 规格
                    Cell cell2 = row.createCell(2);
                    cell2.setCellValue(product.getSpecification() != null ? product.getSpecification() : "");
                    cell2.setCellStyle(cellStyle);

                    // 尺寸
                    Cell cell3 = row.createCell(3);
                    cell3.setCellValue(product.getSize() != null ? product.getSize() : "");
                    cell3.setCellStyle(cellStyle);

                    // 材质
                    Cell cell4 = row.createCell(4);
                    cell4.setCellValue(product.getMaterial() != null ? product.getMaterial() : "");
                    cell4.setCellStyle(cellStyle);

                    // 备注
                    Cell cell5 = row.createCell(5);
                    cell5.setCellValue(product.getRemark() != null ? product.getRemark() : "");
                    cell5.setCellStyle(cellStyle);

                    // 价格
                    Cell cell6 = row.createCell(6);
                    cell6.setCellValue(String.format(Locale.US, "$%.2f", product.getPrice()));
                    cell6.setCellStyle(cellStyle);

                    // 图片
                    Cell cell7 = row.createCell(7);
                    cell7.setCellStyle(cellStyle);

                    if (hasImage) {
                        try {
                            // 读取图片
                            File imageFile = new File(photoPath);
                            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(imageFile));
                            byte[] imageBytes = IOUtils.toByteArray(bis);
                            bis.close();

                            // 判断图片格式
                            int pictureType;
                            String lowerPath = photoPath.toLowerCase();
                            if (lowerPath.endsWith(".png")) {
                                pictureType = Workbook.PICTURE_TYPE_PNG;
                            } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                                pictureType = Workbook.PICTURE_TYPE_JPEG;
                            } else {
                                pictureType = Workbook.PICTURE_TYPE_JPEG; // 默认JPEG
                            }

                            // 添加图片到工作簿
                            int pictureIdx = workbook.addPicture(imageBytes, pictureType);

                            // 创建锚点（定位图片位置）
                            ClientAnchor anchor = helper.createClientAnchor();
                            anchor.setCol1(7);  // 图片左上角列
                            anchor.setRow1(i + 1);  // 图片左上角行
                            anchor.setCol2(8);  // 图片右下角列
                            anchor.setRow2(i + 2);  // 图片右下角行

                            // 插入图片
                            drawing.createPicture(anchor, pictureIdx);
                            imageCount++;

                        } catch (Exception e) {
                            cell7.setCellValue("图片加载失败");
                        }
                    } else {
                        cell7.setCellValue("无图片");
                    }

                    // 话术
                    Cell cell8 = row.createCell(8);
                    cell8.setCellValue(product.getScript() != null ? product.getScript() : "");
                    cell8.setCellStyle(cellStyle);

                    // 创建时间
                    Cell cell9 = row.createCell(9);
                    cell9.setCellValue(product.getCreateTime() > 0 ? dateFormat.format(new Date(product.getCreateTime())) : "");
                    cell9.setCellStyle(cellStyle);

                    // 更新时间
                    Cell cell10 = row.createCell(10);
                    cell10.setCellValue(product.getUpdateTime() > 0 ? dateFormat.format(new Date(product.getUpdateTime())) : "");
                    cell10.setCellStyle(cellStyle);

                    // 更新进度
                    final int progress = i + 1;
                    final int total = products.size();
                    runOnUiThread(() -> {
                        progressDialog.setMessage("正在导出... " + progress + "/" + total);
                    });
                }

                // 写入文件
                workbook.write(outputStream);
                workbook.close();
                outputStream.close();

                final int finalImageCount = imageCount;
                final int productCount = products.size();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, 
                        "导出成功！\n共 " + productCount + " 条数据，" + finalImageCount + " 张图片已嵌入Excel", 
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
