package com.productmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 产品列表适配器
 * 显示产品信息，支持收藏区域和字母区域
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    // 列表项类型
    private static final int TYPE_FAVORITE_HEADER = 0;
    private static final int TYPE_LETTER_HEADER = 1;
    private static final int TYPE_PRODUCT = 2;

    // 列表项包装类
    private static class ListItem {
        final int type;
        final String letter;      // 字母头部的字母
        final Product product;    // 产品
        final boolean isFavoriteSection; // 是否是收藏区域的产品

        ListItem(int type, String letter) {
            this.type = type;
            this.letter = letter;
            this.product = null;
            this.isFavoriteSection = false;
        }

        ListItem(Product product, boolean isFavoriteSection) {
            this.type = TYPE_PRODUCT;
            this.product = product;
            this.letter = null;
            this.isFavoriteSection = isFavoriteSection;
        }
    }

    private List<ListItem> items = new ArrayList<>();
    private OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
        void onProductLongClick(Product product, int position);
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置产品列表，自动处理收藏区域和字母区域
     */
    public void setProducts(List<Product> products) {
        items.clear();

        if (products == null || products.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // 分离收藏产品和普通产品
        List<Product> favoriteProducts = new ArrayList<>();
        List<Product> allProducts = new ArrayList<>();

        for (Product p : products) {
            if (p.isFavorite()) {
                favoriteProducts.add(p);
            }
            allProducts.add(p);
        }

        // 按字母排序收藏产品
        Collections.sort(favoriteProducts, Comparator.comparing(p -> p.getName() != null ? p.getName().toLowerCase() : ""));

        // 按字母排序所有产品
        Collections.sort(allProducts, Comparator.comparing(p -> p.getName() != null ? p.getName().toLowerCase() : ""));

        // 1. 添加收藏区域（如果有收藏产品）
        if (!favoriteProducts.isEmpty()) {
            // 收藏区域头部
            items.add(new ListItem(TYPE_FAVORITE_HEADER, "★"));
            // 收藏区域产品
            for (Product p : favoriteProducts) {
                items.add(new ListItem(p, true));
            }
        }

        // 2. 添加字母区域
        String currentLetter = null;
        for (Product p : allProducts) {
            String letter = p.getFirstLetter();
            if (currentLetter == null || !currentLetter.equals(letter)) {
                currentLetter = letter;
                items.add(new ListItem(TYPE_LETTER_HEADER, letter));
            }
            items.add(new ListItem(p, false));
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        ListItem item = items.get(position);

        switch (item.type) {
            case TYPE_FAVORITE_HEADER:
                holder.bindFavoriteHeader();
                break;
            case TYPE_LETTER_HEADER:
                holder.bindLetterHeader(item.letter);
                break;
            case TYPE_PRODUCT:
                holder.bindProduct(item.product, item.isFavoriteSection);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * 获取指定字母在列表中的位置
     */
    public int getLetterPosition(String letter) {
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            if (item.type == TYPE_LETTER_HEADER && item.letter != null && item.letter.equalsIgnoreCase(letter)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取收藏区域的起始位置
     */
    public int getFavoritePosition() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).type == TYPE_FAVORITE_HEADER) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取当前显示的字母列表（包含★如果有收藏）
     */
    public List<String> getDisplayedLetters() {
        List<String> letters = new ArrayList<>();
        for (ListItem item : items) {
            if (item.type == TYPE_FAVORITE_HEADER) {
                letters.add("★");
            } else if (item.type == TYPE_LETTER_HEADER) {
                letters.add(item.letter);
            }
        }
        return letters;
    }

    class ProductViewHolder extends RecyclerView.ViewHolder {

        private LinearLayout llLetterHeader;
        private ImageView ivHeaderStar;
        private TextView tvLetterHeader;
        private ImageView ivPhoto;
        private TextView tvName;
        private TextView tvSpecification;
        private TextView tvSize;
        private TextView tvMaterial;
        private TextView tvRemark;
        private TextView tvPrice;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            llLetterHeader = itemView.findViewById(R.id.ll_letter_header);
            ivHeaderStar = itemView.findViewById(R.id.iv_header_star);
            tvLetterHeader = itemView.findViewById(R.id.tv_letter_header);
            ivPhoto = itemView.findViewById(R.id.iv_photo);
            tvName = itemView.findViewById(R.id.tv_name);
            tvSpecification = itemView.findViewById(R.id.tv_specification);
            tvSize = itemView.findViewById(R.id.tv_size);
            tvMaterial = itemView.findViewById(R.id.tv_material);
            tvRemark = itemView.findViewById(R.id.tv_remark);
            tvPrice = itemView.findViewById(R.id.tv_price);

            // 产品点击事件
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    ListItem item = items.get(pos);
                    if (item.type == TYPE_PRODUCT && item.product != null) {
                        listener.onProductClick(item.product);
                    }
                }
            });

            // 产品长按事件
            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    ListItem item = items.get(pos);
                    if (item.type == TYPE_PRODUCT && item.product != null) {
                        listener.onProductLongClick(item.product, pos);
                    }
                }
                return true;
            });
        }

        void bindFavoriteHeader() {
            llLetterHeader.setVisibility(View.VISIBLE);
            ivHeaderStar.setVisibility(View.VISIBLE);
            tvLetterHeader.setText("");
            ivPhoto.setVisibility(View.GONE);
            // 隐藏产品信息区域
            itemView.findViewById(R.id.tv_name).setVisibility(View.GONE);
        }

        void bindLetterHeader(String letter) {
            llLetterHeader.setVisibility(View.VISIBLE);
            ivHeaderStar.setVisibility(View.GONE);
            tvLetterHeader.setText(letter);
            ivPhoto.setVisibility(View.GONE);
        }

        void bindProduct(Product product, boolean isFavoriteSection) {
            // 隐藏头部
            llLetterHeader.setVisibility(View.GONE);
            ivPhoto.setVisibility(View.VISIBLE);

            // 设置产品名称
            String name = product.getName();
            tvName.setText(name != null ? name : "");
            tvName.setVisibility(View.VISIBLE);

            // 设置规格
            String specification = product.getSpecification();
            if (specification != null && !specification.isEmpty()) {
                tvSpecification.setText("规格: " + specification);
                tvSpecification.setVisibility(View.VISIBLE);
            } else {
                tvSpecification.setVisibility(View.GONE);
            }

            // 设置尺寸
            String size = product.getSize();
            if (size != null && !size.isEmpty()) {
                tvSize.setText("尺寸: " + size);
                tvSize.setVisibility(View.VISIBLE);
            } else {
                tvSize.setVisibility(View.GONE);
            }

            // 设置材质
            String material = product.getMaterial();
            if (material != null && !material.isEmpty()) {
                tvMaterial.setText("材质: " + material);
                tvMaterial.setVisibility(View.VISIBLE);
            } else {
                tvMaterial.setVisibility(View.GONE);
            }

            // 设置备注
            String remark = product.getRemark();
            if (remark != null && !remark.isEmpty()) {
                tvRemark.setText("备注: " + remark);
                tvRemark.setVisibility(View.VISIBLE);
            } else {
                tvRemark.setVisibility(View.GONE);
            }

            // 设置价格
            tvPrice.setText(product.getFormattedPrice());

            // 设置产品照片
            String photoPath = product.getPhotoPath();
            if (photoPath != null && !photoPath.isEmpty()) {
                File photoFile = new File(photoPath);
                if (photoFile.exists()) {
                    Glide.with(itemView.getContext())
                            .load(photoFile)
                            .placeholder(R.drawable.ic_product)
                            .error(R.drawable.ic_product)
                            .centerCrop()
                            .into(ivPhoto);
                } else {
                    ivPhoto.setImageResource(R.drawable.ic_product);
                }
            } else {
                ivPhoto.setImageResource(R.drawable.ic_product);
            }
        }
    }
}
