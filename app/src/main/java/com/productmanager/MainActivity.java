package com.productmanager;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面
 * 显示产品列表，支持字母索引快速查找
 * 收藏产品在顶部收藏区域显示
 */
public class MainActivity extends AppCompatActivity implements SideIndexBar.OnLetterSelectedListener {

    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private SideIndexBar sideIndexBar;
    private ImageView ivSettings;
    private ImageView ivClear;
    private EditText etSearch;
    private TextView tvEmptyView;
    private TextView tvOverlay;
    private TextView tvProductCount;
    private FloatingActionButton fabAdd;

    private ProductDatabase database;
    private ExecutorService executorService;

    private List<Product> currentProducts = new ArrayList<>();
    private int totalProductCount = 0;
    private boolean hasFavorites = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化数据库
        database = ProductDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();

        // 初始化视图
        initViews();

        // 设置RecyclerView
        setupRecyclerView();

        // 设置搜索功能
        setupSearch();

        // 设置清除按钮
        setupClearButton();

        // 设置字母索引栏
        setupSideIndexBar();

        // 设置添加按钮
        setupFab();

        // 加载数据
        loadProducts();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        sideIndexBar = findViewById(R.id.side_index_bar);
        etSearch = findViewById(R.id.et_search);
        ivClear = findViewById(R.id.iv_clear);
        tvEmptyView = findViewById(R.id.tv_empty_view);
        tvOverlay = findViewById(R.id.tv_overlay);
        tvProductCount = findViewById(R.id.tv_product_count);
        fabAdd = findViewById(R.id.fab_add);
        ivSettings = findViewById(R.id.iv_settings);

        // 设置按钮点击事件
        ivSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnProductClickListener(new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product) {
                Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
                intent.putExtra("product_id", product.getId());
                startActivity(intent);
            }

            @Override
            public void onProductLongClick(Product product, int position) {
                showProductMenu(product);
            }
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateSideIndexHighlight();
            }
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String keyword = s.toString().trim();
                // 显示/隐藏清除按钮
                ivClear.setVisibility(keyword.isEmpty() ? View.GONE : View.VISIBLE);
                searchProducts(keyword);
            }
        });
    }

    private void setupClearButton() {
        ivClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
            // 隐藏软键盘
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        });
    }

    private void setupSideIndexBar() {
        sideIndexBar.setOnLetterSelectedListener(this);
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> {
            // 触发震动反馈
            performHapticFeedback();

            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            startActivity(intent);
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

    private void loadProducts() {
        executorService.execute(() -> {
            List<Product> products = database.productDao().getAllProducts();
            int count = products.size();
            int favoriteCount = database.productDao().getFavoriteCount();
            boolean favorites = favoriteCount > 0;

            runOnUiThread(() -> {
                currentProducts = products;
                totalProductCount = count;
                hasFavorites = favorites;
                adapter.setProducts(products);
                updateEmptyView(products);
                updateSideIndexBar();
                updateProductCountDisplay();
            });
        });
    }

    /**
     * 显示产品操作菜单（收藏/删除）
     */
    private void showProductMenu(Product product) {
        String favoriteText = product.isFavorite() ? "取消收藏" : "收藏";
        String[] options = {favoriteText, "删除"};

        new AlertDialog.Builder(this)
                .setTitle(product.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            toggleFavorite(product);
                            break;
                        case 1:
                            showDeleteConfirmDialog(product);
                            break;
                    }
                })
                .show();
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("删除产品")
                .setMessage("确定要删除 \"" + product.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteProduct(product))
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 切换收藏状态
     */
    private void toggleFavorite(Product product) {
        boolean newStatus = !product.isFavorite();
        executorService.execute(() -> {
            database.productDao().updateFavorite(product.getId(), newStatus);

            runOnUiThread(() -> {
                // 显示提示
                String message = newStatus ? "已收藏: " + product.getName() : "已取消收藏: " + product.getName();
                Snackbar.make(fabAdd, message, Snackbar.LENGTH_SHORT).show();

                // 重新加载产品列表
                loadProducts();
            });
        });
    }

    private void deleteProduct(Product product) {
        executorService.execute(() -> {
            database.productDao().delete(product);
            runOnUiThread(() -> {
                Snackbar.make(fabAdd, "已删除: " + product.getName(), Snackbar.LENGTH_SHORT).show();
                loadProducts();
            });
        });
    }

    private void updateSideIndexBar() {
        // 从adapter获取当前显示的字母列表
        List<String> letters = adapter.getDisplayedLetters();
        sideIndexBar.setLetters(letters);
        sideIndexBar.setHasFavorites(hasFavorites);
    }

    private void searchProducts(String keyword) {
        executorService.execute(() -> {
            List<Product> products;
            if (keyword.isEmpty()) {
                products = database.productDao().getAllProducts();
            } else {
                products = database.productDao().searchProducts(keyword);
            }

            // 检查搜索结果中是否有收藏
            boolean favoritesInResult = false;
            for (Product p : products) {
                if (p.isFavorite()) {
                    favoritesInResult = true;
                    break;
                }
            }

            final int resultCount = products.size();
            final boolean finalFavorites = favoritesInResult;
            runOnUiThread(() -> {
                currentProducts = products;
                hasFavorites = finalFavorites;
                adapter.setProducts(products);
                updateEmptyView(products);
                updateSideIndexBar();
                updateProductCountDisplay(keyword, resultCount);
            });
        });
    }

    private void updateEmptyView(List<Product> products) {
        if (products.isEmpty()) {
            tvEmptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新产品总数显示
     */
    private void updateProductCountDisplay() {
        updateProductCountDisplay("", totalProductCount);
    }

    /**
     * 更新产品总数显示（搜索时）
     */
    private void updateProductCountDisplay(String keyword, int resultCount) {
        if (totalProductCount == 0) {
            tvProductCount.setVisibility(View.GONE);
            return;
        }

        tvProductCount.setVisibility(View.VISIBLE);
        if (keyword.isEmpty()) {
            tvProductCount.setText("共 " + totalProductCount + " 个产品");
        } else {
            tvProductCount.setText("搜索结果: " + resultCount + " / " + totalProductCount + " 个产品");
        }
    }

    @Override
    public void onLetterSelected(String letter) {
        tvOverlay.setText(letter);
        tvOverlay.setVisibility(View.VISIBLE);
        scrollToLetter(letter);
    }

    @Override
    public void onLetterReleased() {
        tvOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onStarSelected() {
        tvOverlay.setText("★");
        tvOverlay.setVisibility(View.VISIBLE);
        scrollToStar();
    }

    private void scrollToLetter(String letter) {
        int position = adapter.getLetterPosition(letter);
        if (position >= 0) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(position, 0);
            }
        }
    }

    private void scrollToStar() {
        int position = adapter.getFavoritePosition();
        if (position >= 0) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(position, 0);
            }
        }
    }

    private void updateSideIndexHighlight() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;

        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePosition >= 0) {
            // 获取当前可见的第一个字母
            List<String> letters = adapter.getDisplayedLetters();
            String currentLetter = null;

            // 从adapter的items中找到当前位置对应的字母
            for (int i = firstVisiblePosition; i >= 0; i--) {
                // 向上查找最近的字母头部
            }

            // 简化处理：从当前位置向下找最近的头部
            for (int i = 0; i <= firstVisiblePosition && i < letters.size(); i++) {
                // 这里需要根据实际位置计算
            }

            // 使用SideIndexBar的方法来更新
            sideIndexBar.clearHighlight();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
