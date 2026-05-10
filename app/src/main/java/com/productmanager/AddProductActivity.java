package com.productmanager;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 添加/编辑产品界面
 */
public class AddProductActivity extends AppCompatActivity {
    
    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_PICK_IMAGE = 101;
    
    private MaterialToolbar toolbar;
    private ImageView ivPhoto;
    private EditText etName;
    private EditText etSpecification;
    private EditText etSize;
    private EditText etPrice;
    private Button btnSave;
    
    private ProductDatabase database;
    private ExecutorService executorService;
    
    private int productId = -1;  // -1表示新增，其他表示编辑
    private Product currentProduct;
    private String currentPhotoPath;
    
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
        etPrice = findViewById(R.id.et_price);
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
        ivPhoto.setOnClickListener(v -> {
            if (checkPermission()) {
                pickImage();
            } else {
                requestPermission();
            }
        });
    }
    
    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> saveProduct());
    }
    
    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage();
            } else {
                Toast.makeText(this, "需要存储权限才能选择照片", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    // 保存图片到应用私有目录
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    
                    // 创建保存路径
                    File imagesDir = new File(getFilesDir(), "product_images");
                    if (!imagesDir.exists()) {
                        imagesDir.mkdirs();
                    }
                    
                    // 生成文件名
                    String fileName = "product_" + System.currentTimeMillis() + ".jpg";
                    File imageFile = new File(imagesDir, fileName);
                    
                    // 保存图片
                    FileOutputStream fos = new FileOutputStream(imageFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                    fos.close();
                    
                    currentPhotoPath = imageFile.getAbsolutePath();
                    
                    // 显示图片
                    Glide.with(this)
                            .load(imageFile)
                            .centerCrop()
                            .into(ivPhoto);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private void loadProduct() {
        executorService.execute(() -> {
            currentProduct = database.productDao().getProductById(productId);
            if (currentProduct != null) {
                runOnUiThread(() -> {
                    etName.setText(currentProduct.getName());
                    etSpecification.setText(currentProduct.getSpecification());
                    etSize.setText(currentProduct.getSize());
                    etPrice.setText(String.valueOf(currentProduct.getPrice()));
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
            }
        });
    }
    
    private void saveProduct() {
        String name = etName.getText().toString().trim();
        String specification = etSpecification.getText().toString().trim();
        String size = etSize.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        
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
                // 编辑
                product = database.productDao().getProductById(productId);
            }
            
            product.setName(name);
            product.setSpecification(specification);
            product.setSize(size);
            product.setPrice(price);
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
