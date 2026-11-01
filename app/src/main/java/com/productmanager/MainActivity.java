package com.productmanager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面
 *
 * 显示产品列表，支持字母索引快速查找；收藏产品在顶部收藏区域显示。
 */
public class MainActivity extends AppCompatActivity implements SideIndexBar.OnLetterSelectedListener {

    /** 搜索防抖间隔：连续输入时只在停顿后查一次库 */
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private SideIndexBar sideIndexBar;
    private ImageView ivClear;
    private EditText etSearch;
    private TextView tvEmptyView;
    private TextView tvOverlay;
    private TextView tvProductCount;
    private FloatingActionButton fabAdd;

    private ProductDatabase database;
    private ExecutorService executorService;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private List<ProductListItem> currentItems = new ArrayList<>();
    private String currentKeyword = "";
    private int totalProductCount = 0;
    private String highlightedLetter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = ProductDatabase.getDatabase(this);
        executorService = Executors.newSingleThreadExecutor();

        initViews();
        setupRecyclerView();
        setupSearch();
        setupClearButton();
        setupSideIndexBar();
        setupFab();
        // 数据加载统一放在 onResume，避免启动时重复查询两次
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

        findViewById(R.id.iv_settings).setOnClickListener(v -> {
            // 从设置页返回后数据可能变化，交给 onResume 刷新
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnProductClickListener(new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(ProductListItem product) {
                Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
                intent.putExtra(AddProductActivity.EXTRA_PRODUCT_ID, product.id);
                startActivity(intent);
            }

            @Override
            public void onProductLongClick(ProductListItem product) {
                showProductMenu(product);
            }
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                syncIndexHighlight();
            }
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String keyword = s.toString().trim();
                ivClear.setVisibility(keyword.isEmpty() ? View.GONE : View.VISIBLE);
                scheduleSearch(keyword);
            }
        });
    }

    private void setupClearButton() {
        ivClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
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
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startActivity(new Intent(MainActivity.this, AddProductActivity.class));
        });
    }

    // ================= 数据加载 =================

    /**
     * 防抖查询：连续输入时只保留最后一次请求
     */
    private void scheduleSearch(String keyword) {
        currentKeyword = keyword;
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> loadItems(keyword);
        handler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void loadItems(String keyword) {
        final String query = keyword == null ? "" : keyword;
        executorService.execute(() -> {
            List<ProductListItem> items = query.isEmpty()
                    ? database.productDao().getAllItems()
                    : database.productDao().searchItems(ProductDao.toLikePattern(query));
            handler.post(() -> submit(query, items));
        });
    }

    private void submit(String keyword, List<ProductListItem> items) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        currentItems = items;
        if (keyword.isEmpty()) {
            totalProductCount = items.size();
        }

        adapter.setItems(items);
        updateEmptyView(items);
        updateSideIndexBar();
        updateProductCountDisplay(keyword, items.size());
        syncIndexHighlight();
    }

    // ================= 交互 =================

    private void showProductMenu(ProductListItem product) {
        String favoriteText = product.isFavorite ? "取消收藏" : "收藏";
        String[] options = {favoriteText, getString(R.string.delete)};

        new AlertDialog.Builder(this)
                .setTitle(product.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        toggleFavorite(product);
                    } else {
                        showDeleteConfirmDialog(product);
                    }
                })
                .show();
    }

    private void showDeleteConfirmDialog(ProductListItem product) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_product_message, safeName(product)))
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteItem(product))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggleFavorite(ProductListItem product) {
        boolean newStatus = !product.isFavorite;
        String name = safeName(product);
        executorService.execute(() -> {
            database.productDao().updateFavorite(product.id, newStatus);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                String message = newStatus
                        ? getString(R.string.favorite_added, name)
                        : getString(R.string.favorite_removed, name);
                Snackbar.make(fabAdd, message, Snackbar.LENGTH_SHORT).show();
                loadItems(currentKeyword);
            });
        });
    }

    /**
     * 删除产品，并在 Snackbar 消失前提供撤销。
     *
     * 图片文件延迟到 Snackbar 关闭时再删：用户点了撤销就还有图。
     */
    private void deleteItem(ProductListItem item) {
        executorService.execute(() -> {
            Product full = database.productDao().getProductById(item.id);
            if (full == null) {
                return;
            }
            database.productDao().delete(full);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    ImageStore.deleteManaged(this, full.getPhotoPath());
                    return;
                }
                loadItems(currentKeyword);

                Snackbar snackbar = Snackbar.make(
                        fabAdd, getString(R.string.product_deleted, safeName(item)), Snackbar.LENGTH_LONG);
                snackbar.setAction(R.string.undo, v -> restoreProduct(full));
                snackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            ImageStore.deleteManaged(MainActivity.this, full.getPhotoPath());
                        }
                    }
                });
                snackbar.show();
            });
        });
    }

    private void restoreProduct(Product product) {
        executorService.execute(() -> {
            database.productDao().insert(product);
            handler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    loadItems(currentKeyword);
                }
            });
        });
    }

    // ================= 视图状态 =================

    private void updateSideIndexBar() {
        sideIndexBar.setItems(adapter.getDisplayedLetters());
    }

    private void updateEmptyView(List<ProductListItem> items) {
        boolean empty = items.isEmpty();
        tvEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateProductCountDisplay(String keyword, int resultCount) {
        if (totalProductCount == 0) {
            tvProductCount.setVisibility(View.GONE);
            return;
        }
        tvProductCount.setVisibility(View.VISIBLE);
        tvProductCount.setText(keyword.isEmpty()
                ? getString(R.string.product_count, totalProductCount)
                : getString(R.string.search_result_count, resultCount, totalProductCount));
    }

    /**
     * 滚动时把右侧索引高亮同步到当前分组（原来这里是空实现）
     */
    private void syncIndexHighlight() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        String letter = adapter.getLetterAtPosition(firstVisible);
        if (letter == null || letter.equals(highlightedLetter)) {
            return;
        }
        highlightedLetter = letter;
        sideIndexBar.highlightLetter(letter);
    }

    private static String safeName(ProductListItem item) {
        return item.name == null ? "" : item.name;
    }

    // ================= 索引回调 =================

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
        scrollToLetter("★");
    }

    private void scrollToLetter(String letter) {
        int position = adapter.getLetterPosition(letter);
        if (position < 0) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    // ================= 生命周期 =================

    @Override
    protected void onResume() {
        super.onResume();
        loadItems(currentKeyword);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executorService.shutdown();
    }
}
