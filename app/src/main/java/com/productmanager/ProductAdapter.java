package com.productmanager;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品列表适配器
 *
 * 结构：收藏区域（可选）→ A-Z 字母分组 → "#" 分组（中文/数字/符号，排在最后）
 *
 * 相比旧实现的三点改进：
 * 1. 分组头使用独立布局，不再为头部 inflate 整套产品视图
 * 2. 数据变化走 DiffUtil，只重绑真正变化的行，保留动画与滚动位置
 * 3. 索引缓存（字母列表 / 字母位置 / 每行所属字母）在数据变化时算一次，
 *    滚动过程中零分配
 */
public class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_FAVORITE_HEADER = 0;
    private static final int TYPE_LETTER_HEADER = 1;
    private static final int TYPE_PRODUCT = 2;

    private List<ListItem> items = new ArrayList<>();

    /** 每行所属的字母分组，用于滚动时高亮右侧索引 */
    private String[] letterOfRow = new String[0];
    /** 当前显示的分组（★ + 字母），供 SideIndexBar 使用 */
    private List<String> displayedLetters = new ArrayList<>();
    /** 字母 → 行号 */
    private final Map<String, Integer> letterPositions = new HashMap<>();

    private OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(ProductListItem product);

        void onProductLongClick(ProductListItem product);
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.listener = listener;
    }

    /**
     * 提交新数据。传入顺序不限，内部会统一排序。
     */
    public void setItems(List<ProductListItem> products) {
        List<ListItem> next = buildItems(products);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new ItemDiffCallback(items, next));

        items = next;
        rebuildCaches();

        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_PRODUCT) {
            return new ProductViewHolder(inflater.inflate(R.layout.item_product, parent, false));
        }
        return new HeaderViewHolder(inflater.inflate(R.layout.item_letter_header, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = items.get(position);
        if (holder instanceof ProductViewHolder) {
            ((ProductViewHolder) holder).bind(item.product);
        } else {
            ((HeaderViewHolder) holder).bind(item);
        }
    }

    // ================= 数据构建 =================

    private static List<ListItem> buildItems(List<ProductListItem> source) {
        List<ListItem> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }

        List<ProductListItem> sorted = ProductListItem.sortedCopy(source);

        // 1. 收藏区域
        List<ProductListItem> favorites = new ArrayList<>();
        for (ProductListItem product : sorted) {
            if (product.isFavorite) {
                favorites.add(product);
            }
        }
        if (!favorites.isEmpty()) {
            out.add(ListItem.favoriteHeader());
            for (ProductListItem product : favorites) {
                out.add(ListItem.product(product, true));
            }
        }

        // 2. 字母分组（sorted 已按 字母 → 名称 排好，"#" 在最后）
        String currentLetter = null;
        for (ProductListItem product : sorted) {
            String letter = product.getFirstLetter();
            if (!letter.equals(currentLetter)) {
                currentLetter = letter;
                out.add(ListItem.letterHeader(letter));
            }
            out.add(ListItem.product(product, false));
        }
        return out;
    }

    private void rebuildCaches() {
        displayedLetters = new ArrayList<>();
        letterPositions.clear();
        letterOfRow = new String[items.size()];

        String current = null;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            if (item.type == TYPE_FAVORITE_HEADER) {
                current = "★";
                displayedLetters.add(current);
                letterPositions.put(current, i);
            } else if (item.type == TYPE_LETTER_HEADER) {
                current = item.letter;
                displayedLetters.add(current);
                letterPositions.put(current, i);
            }
            letterOfRow[i] = current;
        }
    }

    // ================= 供 Activity 查询 =================

    /**
     * 指定字母分组所在行号，没有该分组返回 -1
     */
    public int getLetterPosition(String letter) {
        Integer position = letterPositions.get(letter);
        return position == null ? -1 : position;
    }

    public int getFavoritePosition() {
        return getLetterPosition("★");
    }

    /**
     * 指定行属于哪个字母分组（滚动高亮用）
     */
    public String getLetterAtPosition(int position) {
        if (position < 0 || position >= letterOfRow.length) {
            return null;
        }
        return letterOfRow[position];
    }

    public List<String> getDisplayedLetters() {
        return new ArrayList<>(displayedLetters);
    }

    // ================= 条目包装 =================

    private static class ListItem {
        final int type;
        final String letter;
        final ProductListItem product;
        final boolean favoriteSection;

        private ListItem(int type, String letter, ProductListItem product, boolean favoriteSection) {
            this.type = type;
            this.letter = letter;
            this.product = product;
            this.favoriteSection = favoriteSection;
        }

        static ListItem favoriteHeader() {
            return new ListItem(TYPE_FAVORITE_HEADER, "★", null, false);
        }

        static ListItem letterHeader(String letter) {
            return new ListItem(TYPE_LETTER_HEADER, letter, null, false);
        }

        static ListItem product(ProductListItem product, boolean favoriteSection) {
            return new ListItem(TYPE_PRODUCT, null, product, favoriteSection);
        }
    }

    private static boolean sameContent(ProductListItem a, ProductListItem b) {
        return a.id == b.id
                && a.isFavorite == b.isFavorite
                && Double.compare(a.price, b.price) == 0
                && TextUtils.equals(a.name, b.name)
                && TextUtils.equals(a.specification, b.specification)
                && TextUtils.equals(a.size, b.size)
                && TextUtils.equals(a.material, b.material)
                && TextUtils.equals(a.remark, b.remark)
                && TextUtils.equals(a.photoPath, b.photoPath);
    }

    private static class ItemDiffCallback extends DiffUtil.Callback {

        private final List<ListItem> oldItems;
        private final List<ListItem> newItems;

        ItemDiffCallback(List<ListItem> oldItems, List<ListItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            ListItem oldItem = oldItems.get(oldPos);
            ListItem newItem = newItems.get(newPos);
            if (oldItem.type != newItem.type) {
                return false;
            }
            if (oldItem.type == TYPE_PRODUCT) {
                return oldItem.product.id == newItem.product.id;
            }
            return TextUtils.equals(oldItem.letter, newItem.letter);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            ListItem oldItem = oldItems.get(oldPos);
            ListItem newItem = newItems.get(newPos);
            if (oldItem.type == TYPE_PRODUCT) {
                return sameContent(oldItem.product, newItem.product);
            }
            // 分组头内容由字母决定，字母相同即内容相同
            return true;
        }
    }

    // ================= ViewHolder =================

    private class HeaderViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivStar;
        private final TextView tvLetter;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            ivStar = itemView.findViewById(R.id.iv_header_star);
            tvLetter = itemView.findViewById(R.id.tv_letter_header);
        }

        void bind(ListItem item) {
            boolean isFavoriteHeader = item.type == TYPE_FAVORITE_HEADER;
            ivStar.setVisibility(isFavoriteHeader ? View.VISIBLE : View.GONE);
            tvLetter.setText(isFavoriteHeader ? "" : item.letter);
        }
    }

    private class ProductViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivPhoto;
        private final TextView tvName;
        private final TextView tvSpecification;
        private final TextView tvSize;
        private final TextView tvMaterial;
        private final TextView tvRemark;
        private final TextView tvPrice;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_photo);
            tvName = itemView.findViewById(R.id.tv_name);
            tvSpecification = itemView.findViewById(R.id.tv_specification);
            tvSize = itemView.findViewById(R.id.tv_size);
            tvMaterial = itemView.findViewById(R.id.tv_material);
            tvRemark = itemView.findViewById(R.id.tv_remark);
            tvPrice = itemView.findViewById(R.id.tv_price);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) {
                    return;
                }
                ListItem item = items.get(position);
                if (item.type == TYPE_PRODUCT && item.product != null) {
                    listener.onProductClick(item.product);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listener == null) {
                    return false;
                }
                ListItem item = items.get(position);
                if (item.type == TYPE_PRODUCT && item.product != null) {
                    listener.onProductLongClick(item.product);
                }
                return true;
            });
        }

        void bind(ProductListItem product) {
            tvName.setText(product.name == null ? "" : product.name);

            bindOptional(tvSpecification, R.string.specification, product.specification);
            bindOptional(tvSize, R.string.size, product.size);
            bindOptional(tvMaterial, R.string.material, product.material);
            bindOptional(tvRemark, R.string.remark, product.remark);

            tvPrice.setText(product.getFormattedPrice());

            String photoPath = product.photoPath;
            if (photoPath != null && !photoPath.isEmpty() && new File(photoPath).exists()) {
                Glide.with(ivPhoto)
                        .load(new File(photoPath))
                        .placeholder(R.drawable.ic_product)
                        .error(R.drawable.ic_product)
                        .centerCrop()
                        .into(ivPhoto);
            } else {
                Glide.with(ivPhoto).clear(ivPhoto);
                ivPhoto.setImageResource(R.drawable.ic_product);
            }
        }

        private void bindOptional(TextView target, int labelRes, String value) {
            if (value != null && !value.isEmpty()) {
                target.setText(target.getContext().getString(
                        R.string.field_with_value, target.getContext().getString(labelRes), value));
                target.setVisibility(View.VISIBLE);
            } else {
                target.setVisibility(View.GONE);
            }
        }
    }
}
