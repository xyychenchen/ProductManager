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
import java.util.List;
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
            runOnUiThread(() -> {
                adapter.setProducts(products);
                updateEmptyView(products);
            });
        });
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
                adapter.setProducts(products);
                updateEmptyView(products);
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
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        // 获取适配器中的产品列表
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            // 这里需要从适配器获取产品列表
        }
        
        // 找到第一个匹配的字母位置
        executorService.execute(() -> {
            List<Product> allProducts = database.productDao().getProductsByFirstLetter(letter);
            if (!allProducts.isEmpty()) {
                int targetId = allProducts.get(0).getId();
                runOnUiThread(() -> {
                    // 在当前显示的列表中找到位置
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        // 简化处理：滚动到顶部让用户看到
                    }
                });
            }
        });
    }
    
    private void updateSideIndexHighlight() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        // 更新字母索引栏的高亮
        // 这里需要获取当前位置的产品首字母
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
