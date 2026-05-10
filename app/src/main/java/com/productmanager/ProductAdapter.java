package com.productmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品列表适配器
 * 显示产品信息，包括名称、规格、材质、价格
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private List<Product> products = new ArrayList<>();
    private OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
        void onProductLongClick(Product product, int position);
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.listener = listener;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
        notifyDataSetChanged();
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
        Product product = products.get(position);
        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    class ProductViewHolder extends RecyclerView.ViewHolder {

        private ImageView ivPhoto;
        private TextView tvName;
        private TextView tvSpecification;
        private TextView tvSize;
        private TextView tvMaterial;
        private TextView tvPrice;
        private TextView tvLetterHeader;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_photo);
            tvName = itemView.findViewById(R.id.tv_name);
            tvSpecification = itemView.findViewById(R.id.tv_specification);
            tvSize = itemView.findViewById(R.id.tv_size);
            tvMaterial = itemView.findViewById(R.id.tv_material);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvLetterHeader = itemView.findViewById(R.id.tv_letter_header);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProductClick(products.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProductLongClick(products.get(pos), pos);
                }
                return true;
            });
        }

        public void bind(Product product) {
            // 设置产品名称（添加空值检查）
            String name = product.getName();
            tvName.setText(name != null ? name : "");

            // 设置规格（添加空值检查）
            String specification = product.getSpecification();
            if (specification != null && !specification.isEmpty()) {
                tvSpecification.setText("规格: " + specification);
                tvSpecification.setVisibility(View.VISIBLE);
            } else {
                tvSpecification.setVisibility(View.GONE);
            }

            // 设置尺寸（添加空值检查）
            String size = product.getSize();
            if (size != null && !size.isEmpty()) {
                tvSize.setText("尺寸: " + size);
                tvSize.setVisibility(View.VISIBLE);
            } else {
                tvSize.setVisibility(View.GONE);
            }

            // 设置材质（添加空值检查）
            String material = product.getMaterial();
            if (material != null && !material.isEmpty()) {
                tvMaterial.setText("材质: " + material);
                tvMaterial.setVisibility(View.VISIBLE);
            } else {
                tvMaterial.setVisibility(View.GONE);
            }

            // 设置价格（美金格式）
            tvPrice.setText(product.getFormattedPrice());

            // 设置产品照片（添加空值检查）
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

            // 设置字母头部
            String letter = product.getFirstLetter();
            // 判断是否需要显示字母头部
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            if (position == 0) {
                tvLetterHeader.setText(letter);
                tvLetterHeader.setVisibility(View.VISIBLE);
            } else if (position > 0 && position <= products.size() - 1) {
                Product prevProduct = products.get(position - 1);
                if (!prevProduct.getFirstLetter().equals(letter)) {
                    tvLetterHeader.setText(letter);
                    tvLetterHeader.setVisibility(View.VISIBLE);
                } else {
                    tvLetterHeader.setVisibility(View.GONE);
                }
            }
        }
    }
}
