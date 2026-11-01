package com.productmanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 添加/编辑产品界面
 *
 * 图片处理约定：
 * - 相册与相机的结果都经过 {@link ImageStore} 压缩成统一尺寸后落盘，不再保存原图
 * - 相机使用 cache 目录中的临时文件，压缩完成后立刻删除
 * - 编辑时替换图片，旧文件在保存成功后才删除（用户中途放弃不会丢图）
 */
public class AddProductActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";

    private static final String STATE_NAME = "state_name";
    private static final String STATE_SPECIFICATION = "state_specification";
    private static final String STATE_SIZE = "state_size";
    private static final String STATE_MATERIAL = "state_material";
    private static final String STATE_PRICE = "state_price";
    private static final String STATE_REMARK = "state_remark";
    private static final String STATE_SCRIPT = "state_script";
    private static final String STATE_PHOTO_PATH = "state_photo_path";
    private static final String STATE_ORIGINAL_PHOTO = "state_original_photo";
    private static final String STATE_CAMERA_TEMP = "state_camera_temp";

    private static final String FILE_PROVIDER_AUTHORITY = "com.productmanager.fileprovider";

    private MaterialToolbar toolbar;
    private ImageView ivPhoto;
    private EditText etName;
    private EditText etSpecification;
    private EditText etSize;
    private EditText etMaterial;
    private EditText etPrice;
    private EditText etRemark;
    private EditText etScript;
    private Button btnSave;
    private TextView tvFullscreenEdit;

    private ProductDatabase database;
    private ExecutorService executorService;

    private int productId = -1;
    private Product currentProduct;
    /** 当前展示/待保存的图片 */
    private String currentPhotoPath;
    /** 进入页面时的原始图片，保存成功且被替换后才删除 */
    private String originalPhotoPath;
    /** 相机临时文件，压缩或放弃后清理 */
    private String cameraTempPath;
    private boolean photoProcessing;
    /** 本次编辑是否已成功保存，用于判断退出时是否需要清理新选的图片 */
    private boolean saved;

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        database = ProductDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();

        productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);

        initViews();
        registerLaunchers();
        setupToolbar();
        setupPhotoPicker();
        setupMarkdownPreviewButton();
        setupSaveButton();

        if (savedInstanceState != null) {
            // 屏幕旋转 / 进程恢复：优先恢复用户输入，避免被数据库中的旧值覆盖
            restoreState(savedInstanceState);
        } else if (productId != -1) {
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
        etRemark = findViewById(R.id.et_remark);
        etScript = findViewById(R.id.et_script);
        btnSave = findViewById(R.id.btn_save);
        tvFullscreenEdit = findViewById(R.id.tv_fullscreen_edit);
    }

    private void registerLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        importPickedImage(result.getData().getData());
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success) {
                        importCameraPhoto();
                    } else {
                        discardCameraTemp();
                    }
                });
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(productId == -1 ? R.string.add_product : R.string.edit_product);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPhotoPicker() {
        ivPhoto.setOnClickListener(v -> showPhotoChooseDialog());
    }

    private void setupMarkdownPreviewButton() {
        tvFullscreenEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScriptViewerActivity.class);
            // 传 id 而不是整段文本，避免长话术触发 Binder 事务上限
            intent.putExtra(ScriptViewerActivity.EXTRA_PRODUCT_ID, productId);
            intent.putExtra(ScriptViewerActivity.EXTRA_PRODUCT_NAME, etName.getText().toString());
            intent.putExtra(ScriptViewerActivity.EXTRA_SCRIPT_DRAFT, etScript.getText().toString());
            startActivity(intent);
        });
    }

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveProduct();
        });
    }

    // ================= 图片 =================

    private void showPhotoChooseDialog() {
        if (photoProcessing) {
            return;
        }
        String[] options = {
                getString(R.string.photo_pick_camera),
                getString(R.string.photo_pick_gallery)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.photo_pick_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        takePhoto();
                    } else {
                        pickImage();
                    }
                })
                .show();
    }

    /**
     * 相册走系统文件选择器（ACTION_GET_CONTENT），无需申请存储权限
     */
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            pickImageLauncher.launch(intent);
        } catch (Exception e) {
            toast(R.string.image_load_failed);
        }
    }

    private void importPickedImage(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        photoProcessing = true;
        executorService.execute(() -> {
            String stored = ImageStore.importImage(getApplicationContext(), uri);
            runOnUiThread(() -> {
                photoProcessing = false;
                if (isFinishing() || isDestroyed()) {
                    if (stored != null) {
                        ImageStore.deleteManaged(this, stored);
                    }
                    return;
                }
                if (stored != null) {
                    applyPhoto(stored);
                } else {
                    toast(R.string.image_load_failed);
                }
            });
        });
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建临时目录");
            }
            File temp = new File(dir, "camera_" + System.currentTimeMillis() + ".jpg");
            cameraTempPath = temp.getAbsolutePath();
            Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, temp);
            takePictureLauncher.launch(uri);
        } catch (Exception e) {
            discardCameraTemp();
            toast(R.string.no_camera_app);
        }
    }

    private void importCameraPhoto() {
        final String tempPath = cameraTempPath;
        cameraTempPath = null;
        if (tempPath == null) {
            return;
        }
        photoProcessing = true;
        executorService.execute(() -> {
            String stored = ImageStore.importCameraTempFile(getApplicationContext(), tempPath);
            runOnUiThread(() -> {
                photoProcessing = false;
                if (isFinishing() || isDestroyed()) {
                    if (stored != null) {
                        ImageStore.deleteManaged(this, stored);
                    }
                    return;
                }
                if (stored != null) {
                    applyPhoto(stored);
                } else {
                    toast(R.string.image_save_failed);
                }
            });
        });
    }

    private void applyPhoto(String path) {
        // 旧的待保存图片直接清理（此时还没写入数据库）
        if (currentPhotoPath != null && !currentPhotoPath.equals(originalPhotoPath)) {
            ImageStore.deleteManaged(this, currentPhotoPath);
        }
        currentPhotoPath = path;
        Glide.with(this)
                .load(new File(path))
                .centerCrop()
                .into(ivPhoto);
    }

    private void discardCameraTemp() {
        if (cameraTempPath != null) {
            ImageStore.deleteQuietly(cameraTempPath);
            cameraTempPath = null;
        }
    }

    // ================= 加载与保存 =================

    private void loadProduct() {
        executorService.execute(() -> {
            Product product = database.productDao().getProductById(productId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (product == null) {
                    toast(R.string.product_missing);
                    finish();
                    return;
                }
                currentProduct = product;
                etName.setText(nullToEmpty(product.getName()));
                etSpecification.setText(nullToEmpty(product.getSpecification()));
                etSize.setText(nullToEmpty(product.getSize()));
                etMaterial.setText(nullToEmpty(product.getMaterial()));
                etRemark.setText(nullToEmpty(product.getRemark()));
                etScript.setText(nullToEmpty(product.getScript()));
                if (product.getPrice() != 0) {
                    etPrice.setText(String.format(Locale.US, "%.2f", product.getPrice()));
                }
                originalPhotoPath = product.getPhotoPath();
                currentPhotoPath = originalPhotoPath;
                showPhoto(originalPhotoPath);
            });
        });
    }

    private void showPhoto(String path) {
        if (path != null && !path.isEmpty() && new File(path).exists()) {
            Glide.with(this).load(new File(path)).centerCrop().into(ivPhoto);
        } else {
            ivPhoto.setImageResource(R.drawable.ic_product);
        }
    }

    private void saveProduct() {
        String name = etName.getText().toString().trim();
        String specification = etSpecification.getText().toString().trim();
        String size = etSize.getText().toString().trim();
        String material = etMaterial.getText().toString().trim();
        String priceText = etPrice.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();
        String script = etScript.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            toast(R.string.name_required);
            return;
        }
        if (TextUtils.isEmpty(priceText)) {
            toast(R.string.price_required);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            toast(R.string.price_invalid);
            return;
        }
        if (!isValidPrice(price)) {
            toast(R.string.price_invalid);
            return;
        }

        final double finalPrice = price;
        final boolean isNewProduct = (productId == -1);
        final String photoToSave = currentPhotoPath;
        final String photoToDelete = (originalPhotoPath != null && !originalPhotoPath.equals(photoToSave))
                ? originalPhotoPath
                : null;

        btnSave.setEnabled(false);
        executorService.execute(() -> {
            Product product;
            if (isNewProduct) {
                product = new Product();
            } else {
                if (currentProduct == null) {
                    currentProduct = database.productDao().getProductById(productId);
                }
                if (currentProduct == null) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        toast(R.string.product_missing_cannot_save);
                        finish();
                    });
                    return;
                }
                product = currentProduct;
            }

            product.setName(name);
            product.setSpecification(specification);
            product.setSize(size);
            product.setMaterial(material);
            product.setRemark(remark);
            product.setPrice(finalPrice);
            product.setScript(script);
            if (photoToSave != null) {
                product.setPhotoPath(photoToSave);
            }

            if (isNewProduct) {
                database.productDao().insert(product);
            } else {
                database.productDao().update(product);
            }

            // 保存成功后才回收被替换掉的旧图
            if (photoToDelete != null) {
                ImageStore.deleteManaged(getApplicationContext(), photoToDelete);
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                saved = true;
                toast(R.string.save_success);
                finish();
            });
        });
    }

    /**
     * 价格校验：拒绝 NaN / Infinity / 负数 / 超范围值
     */
    private static boolean isValidPrice(double price) {
        return !Double.isNaN(price) && !Double.isInfinite(price) && price >= 0 && price < 1_000_000_000d;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ================= 状态保存 =================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_NAME, etName.getText().toString());
        outState.putString(STATE_SPECIFICATION, etSpecification.getText().toString());
        outState.putString(STATE_SIZE, etSize.getText().toString());
        outState.putString(STATE_MATERIAL, etMaterial.getText().toString());
        outState.putString(STATE_PRICE, etPrice.getText().toString());
        outState.putString(STATE_REMARK, etRemark.getText().toString());
        outState.putString(STATE_SCRIPT, etScript.getText().toString());
        outState.putString(STATE_PHOTO_PATH, currentPhotoPath);
        outState.putString(STATE_ORIGINAL_PHOTO, originalPhotoPath);
        outState.putString(STATE_CAMERA_TEMP, cameraTempPath);
    }

    private void restoreState(Bundle state) {
        etName.setText(state.getString(STATE_NAME, ""));
        etSpecification.setText(state.getString(STATE_SPECIFICATION, ""));
        etSize.setText(state.getString(STATE_SIZE, ""));
        etMaterial.setText(state.getString(STATE_MATERIAL, ""));
        etPrice.setText(state.getString(STATE_PRICE, ""));
        etRemark.setText(state.getString(STATE_REMARK, ""));
        etScript.setText(state.getString(STATE_SCRIPT, ""));

        originalPhotoPath = state.getString(STATE_ORIGINAL_PHOTO);
        currentPhotoPath = state.getString(STATE_PHOTO_PATH);
        cameraTempPath = state.getString(STATE_CAMERA_TEMP);
        showPhoto(currentPhotoPath);

        // 编辑模式下仍需数据库实体用于更新，但不覆盖输入框
        if (productId != -1) {
            executorService.execute(() -> {
                Product product = database.productDao().getProductById(productId);
                if (product != null) {
                    currentProduct = product;
                }
            });
        }
    }

    // ================= 生命周期 =================

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        discardCameraTemp();

        // 用户放弃编辑（未保存就退出）时，回收这次新生成但没入库的图片；
        // 屏幕旋转等重建场景 isFinishing() 为 false，不会误删
        if (isFinishing() && !saved && currentPhotoPath != null
                && !currentPhotoPath.equals(originalPhotoPath)) {
            ImageStore.deleteManaged(this, currentPhotoPath);
        }

        executorService.shutdown();
    }
}
