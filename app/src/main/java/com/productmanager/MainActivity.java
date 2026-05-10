package com.productmanager;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
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
 */
public class MainActivity extends AppCompatActivity implements SideIndexBar.OnLetterSelectedListener {
    
    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private SideIndexBar sideIndexBar;
    private EditText etSearch;
    private TextView tvEmptyView;
    private TextView tvOverlay;
    private FloatingActionButton fabAdd;
    
    private ProductDatabase database;
    private ExecutorService executorService;
    
    private List<Product> currentProducts = new ArrayList<>();  // 当前显示的产品列表
    
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
        tvEmptyView = findViewById(R.id.tv_empty_view);
        tvOverlay = findViewById(R.id.tv_overlay);
        fabAdd = findViewById(R.id.fab_add);
    }
    
    private void setupRecyclerView() {
        adapter = new ProductAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        // 点击编辑
        adapter.setOnProductClickListener(new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product) {
                Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
                intent.putExtra("product_id", product.getId());
                startActivity(intent);
            }
            
            @Override
            public void onProductLongClick(Product product, int position) {
                showDeleteDialog(product);
            }
        });
        
        // 滚动监听，更新字母索引栏高亮
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
                searchProducts(s.toString().trim());
            }
        });
    }
    
    private void setupSideIndexBar() {
        sideIndexBar.setOnLetterSelectedListener(this);
    }
    
    private void setupFab() {
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            startActivity(intent);
        });
    }
    
    private void loadProducts() {
        executorService.execute(() -> {
            List<Product> products = database.productDao().getAllProducts();
            List<String> usedLetters = database.productDao().getUsedLetters();
            
            runOnUiThread(() -> {
                currentProducts = products;
                adapter.setProducts(products);
                updateEmptyView(products);
                
                // 更新字母索引栏只显示有产品的首字母
                updateSideIndexBar(usedLetters);
            });
        });
    }
    
    /**
     * 更新字母索引栏显示的字母
     * 只显示有产品的首字母
     */
    private void updateSideIndexBar(List<String> usedLetters) {
        if (usedLetters == null || usedLetters.isEmpty()) {
            sideIndexBar.setLetters(new ArrayList<>());
            return;
        }
        
        // 过滤出有效的字母
        List<String> validLetters = new ArrayList<>();
        for (String letter : usedLetters) {
            if (letter != null && !letter.isEmpty()) {
                validLetters.add(letter.toUpperCase());
            }
        }
        
        // 去重
        Set<String> uniqueLetters = new HashSet<>(validLetters);
        validLetters = new ArrayList<>(uniqueLetters);
        
        sideIndexBar.setLetters(validLetters);
    }
    
    private void searchProducts(String keyword) {
        executorService.execute(() -> {
            List<Product> products;
            if (keyword.isEmpty()) {
                products = database.productDao().getAllProducts();
            } else {
                products = database.productDao().searchProducts(keyword);
            }
            
            runOnUiThread(() -> {
                currentProducts = products;
                adapter.setProducts(products);
                updateEmptyView(products);
                
                // 搜索时也更新字母栏
                if (keyword.isEmpty()) {
                    // 恢复完整字母栏
                    List<String> usedLetters = database.productDao().getUsedLetters();
                    updateSideIndexBar(usedLetters);
                } else {
                    // 搜索结果对应的字母
                    Set<String> letters = new HashSet<>();
                    for (Product p : products) {
                        String firstLetter = p.getFirstLetter();
                        if (firstLetter != null && !firstLetter.isEmpty()) {
                            letters.add(firstLetter.toUpperCase());
                        }
                    }
                    sideIndexBar.setLetters(new ArrayList<>(letters));
                }
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
    
    @Override
    public void onLetterSelected(String letter) {
        // 显示字母覆盖层
        tvOverlay.setText(letter);
        tvOverlay.setVisibility(View.VISIBLE);
        
        // 滚动到对应位置
        scrollToLetter(letter);
    }
    
    @Override
    public void onLetterReleased() {
        // 隐藏字母覆盖层
        tvOverlay.setVisibility(View.GONE);
    }
    
    private void scrollToLetter(String letter) {
        // 在当前产品列表中找到第一个匹配的字母位置
        for (int i = 0; i < currentProducts.size(); i++) {
            Product product = currentProducts.get(i);
            if (product.getFirstLetter().equalsIgnoreCase(letter)) {
                // 滚动到该位置
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(i, 0);
                }
                break;
            }
        }
    }
    
    private void updateSideIndexHighlight() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null || currentProducts.isEmpty()) return;
        
        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePosition >= 0 && firstVisiblePosition < currentProducts.size()) {
            Product product = currentProducts.get(firstVisiblePosition);
            String letter = product.getFirstLetter();
            sideIndexBar.highlightLetter(letter);
        }
    }
    
    private void showDeleteDialog(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("删除产品")
                .setMessage("确定要删除 \"" + product.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteProduct(product))
                .setNegativeButton("取消", null)
                .show();
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
