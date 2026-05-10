package com.productmanager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 添加/编辑产品界面
 */
public class AddProductActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_PICK_IMAGE = 102;
    private static final int REQUEST_TAKE_PHOTO = 103;

    private MaterialToolbar toolbar;
    private ImageView ivPhoto;
    private EditText etName;
    private EditText etSpecification;
    private EditText etSize;
    private EditText etMaterial;
    private EditText etPrice;
    private EditText etScript;
    private Button btnSave;

    private ProductDatabase database;
    private ExecutorService executorService;

    private int productId = -1;  // -1表示新增，其他表示编辑
    private Product currentProduct;
    private String currentPhotoPath;
    private String cameraPhotoPath;  // 相机拍照的实际文件路径

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        // 初始化数据库
        database = ProductDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();

        // 获取传入的产品ID
        productId = getIntent().getIntExtra("product_id", -1);

        // 初始化视图
        initViews();

        // 设置工具栏
        setupToolbar();

        // 设置照片点击
        setupPhotoPicker();

        // 设置保存按钮
        setupSaveButton();

        // 加载产品数据（编辑模式）
        if (productId != -1) {
            loadProduct();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivPhoto = findViewById(R.id.iv_photo);
        etName = findViewById(R.id.et_name);
        etSpecification = findViewById(R.id.et_specification);
        etSize = findViewById(R.id.et_size);
        etMaterial = findViewById(R.id.et_material);
        etPrice = findViewById(R.id.et_price);
        etScript = findViewById(R.id.et_script);
        btnSave = findViewById(R.id.btn_save);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(productId == -1 ? "添加产品" : "编辑产品");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPhotoPicker() {
        ivPhoto.setOnClickListener(v -> showPhotoChooseDialog());
    }

    /**
     * 显示选择照片方式对话框
     */
    private void showPhotoChooseDialog() {
        String[] options = {"拍照", "从相册选择"};
        new AlertDialog.Builder(this)
                .setTitle("选择照片")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // 拍照
                        checkAndTakePhoto();
                    } else {
                        // 从相册选择
                        checkAndPickImage();
                    }
                })
                .show();
    }

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> {
            // 触发震动反馈
            performHapticFeedback();
            saveProduct();
        });
    }

    /**
     * 执行震动反馈
     */
    private void performHapticFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(30);
            }
        }
    }

    // ============ 权限检查和请求 ============

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查权限并从相册选择
     */
    private void checkAndPickImage() {
        if (checkStoragePermission()) {
            pickImage();
        } else {
            requestStoragePermission();
        }
    }

    /**
     * 检查权限并拍照
     */
    private void checkAndTakePhoto() {
        if (checkCameraPermission()) {
            takePhoto();
        } else {
            requestCameraPermission();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_STORAGE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限获取成功，直接执行用户选择的操作
            if (requestCode == REQUEST_STORAGE_PERMISSION) {
                // 相册权限，直接打开相册
                pickImage();
            } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
                // 相机权限，直接打开相机
                takePhoto();
            }
        } else {
            Toast.makeText(this, "需要相应权限才能继续操作", Toast.LENGTH_SHORT).show();
        }
    }

    // ============ 拍照功能 ============

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // 创建临时文件保存拍照结果
            File photoFile = createImageFile();
            if (photoFile != null) {
                cameraPhotoPath = photoFile.getAbsolutePath();  // 保存实际文件路径

                Uri photoUri = FileProvider.getUriForFile(this,
                        "com.productmanager.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                // 给 Intent 添加读取权限
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private File createImageFile() {
        // 创建保存路径
        File imagesDir = new File(getFilesDir(), "product_images");
        if (!imagesDir.exists()) {
            imagesDir.mkdirs();
        }

        // 生成文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";

        return new File(imagesDir, fileName);
    }

    // ============ 从相册选择 ============

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    // ============ 处理结果 ============

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_PICK_IMAGE:
                // 从相册选择
                if (data != null && data.getData() != null) {
                    handlePickedImage(data.getData());
                }
                break;

            case REQUEST_TAKE_PHOTO:
                // 拍照 - 直接使用保存的文件路径
                if (cameraPhotoPath != null) {
                    handleTakenPhoto();
                }
                break;
        }
    }

    /**
     * 处理从相册选择的图片
     */
    private void handlePickedImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (bitmap != null) {
                // 保存图片到应用私有目录
                currentPhotoPath = saveBitmapToFile(bitmap);

                // 显示图片
                if (currentPhotoPath != null) {
                    Glide.with(this)
                            .load(new File(currentPhotoPath))
                            .centerCrop()
                            .into(ivPhoto);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理拍照结果
     */
    private void handleTakenPhoto() {
        try {
            File photoFile = new File(cameraPhotoPath);

            if (photoFile.exists()) {
                // 直接使用拍照保存的文件作为产品图片
                currentPhotoPath = cameraPhotoPath;

                // 显示图片
                Glide.with(this)
                        .load(photoFile)
                        .centerCrop()
                        .into(ivPhoto);
            } else {
                Toast.makeText(this, "照片保存失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 保存 Bitmap 到文件
     */
    private String saveBitmapToFile(Bitmap bitmap) {
        try {
            File imagesDir = new File(getFilesDir(), "product_images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            String fileName = "product_" + System.currentTimeMillis() + ".jpg";
            File imageFile = new File(imagesDir, fileName);

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.close();

            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ============ 加载和保存产品 ============

    private void loadProduct() {
        executorService.execute(() -> {
            currentProduct = database.productDao().getProductById(productId);
            if (currentProduct != null) {
                runOnUiThread(() -> {
                    etName.setText(currentProduct.getName() != null ? currentProduct.getName() : "");
                    etSpecification.setText(currentProduct.getSpecification() != null ? currentProduct.getSpecification() : "");
                    etSize.setText(currentProduct.getSize() != null ? currentProduct.getSize() : "");
                    etMaterial.setText(currentProduct.getMaterial() != null ? currentProduct.getMaterial() : "");
                    etScript.setText(currentProduct.getScript() != null ? currentProduct.getScript() : "");
                    
                    if (currentProduct.getPrice() != 0) {
                        etPrice.setText(String.valueOf(currentProduct.getPrice()));
                    }
                    
                    currentPhotoPath = currentProduct.getPhotoPath();

                    if (currentPhotoPath != null && !currentPhotoPath.isEmpty()) {
                        File photoFile = new File(currentPhotoPath);
                        if (photoFile.exists()) {
                            Glide.with(this)
                                    .load(photoFile)
                                    .centerCrop()
                                    .into(ivPhoto);
                        }
                    }
                });
            } else {
                // 产品不存在，返回主页面
                runOnUiThread(() -> {
                    Toast.makeText(this, "产品不存在", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void saveProduct() {
        String name = etName.getText().toString().trim();
        String specification = etSpecification.getText().toString().trim();
        String size = etSize.getText().toString().trim();
        String material = etMaterial.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String script = etScript.getText().toString().trim();

        // 验证必填项
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请输入产品名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(priceStr)) {
            Toast.makeText(this, "请输入产品价格", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "价格格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存产品
        executorService.execute(() -> {
            Product product;
            if (productId == -1) {
                // 新增
                product = new Product();
            } else {
                // 编辑 - 使用已加载的 currentProduct
                if (currentProduct != null) {
                    product = currentProduct;
                } else {
                    // 如果 currentProduct 为空，尝试重新获取
                    product = database.productDao().getProductById(productId);
                    if (product == null) {
                        // 产品不存在，创建新的
                        product = new Product();
                    }
                }
            }

            product.setName(name);
            product.setSpecification(specification);
            product.setSize(size);
            product.setMaterial(material);
            product.setPrice(price);
            product.setScript(script);
            if (currentPhotoPath != null) {
                product.setPhotoPath(currentPhotoPath);
            }

            if (productId == -1) {
                database.productDao().insert(product);
            } else {
                database.productDao().update(product);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
