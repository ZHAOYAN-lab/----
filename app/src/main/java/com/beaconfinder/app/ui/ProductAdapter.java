package com.beaconfinder.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.beaconfinder.app.R;
import com.beaconfinder.app.data.WarehouseStore;
import com.beaconfinder.app.databinding.ItemProductBinding;
import com.beaconfinder.app.model.Product;

import java.util.ArrayList;
import java.util.List;

public final class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.Holder> {
    public interface Listener {
        void onEdit(Product product);
        void onDelete(Product product);
        void onBind(Product product);
        void onUnbind(Product product);
        void onTestLight(Product product);
    }

    private final Listener listener;
    private final List<Product> items = new ArrayList<>();

    public ProductAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Product> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemProductBinding binding;

        Holder(ItemProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Product product) {
            WarehouseStore store = WarehouseStore.getInstance(binding.getRoot().getContext());

            binding.productName.setText(product.name);
            binding.productSku.setText((product.sku != null && !product.sku.trim().isEmpty())
                    ? "SKU: " + product.sku
                    : com.beaconfinder.app.data.AppLanguage.text("SKU: 未設定"));

            String whName = store.getWarehouseName(product.warehouseId);
            String arName = store.getAreaName(product.areaId);
            if (product.shelf != null && !product.shelf.isEmpty()) arName += " / " + product.shelf;
            StringBuilder loc = new StringBuilder("📍 ");
            if (!whName.isEmpty()) loc.append(whName);
            if (!arName.isEmpty()) {
                if (!whName.isEmpty()) loc.append(" · ");
                loc.append(arName);
            }
            if (loc.length() <= 3) loc.append(com.beaconfinder.app.data.AppLanguage.text("倉庫未指定"));
            binding.productLocation.setText(loc.toString());

            if (product.memo != null && !product.memo.trim().isEmpty()) {
                binding.productMemo.setVisibility(View.VISIBLE);
                binding.productMemo.setText(com.beaconfinder.app.data.AppLanguage.text("備考: ") + product.memo.trim());
            } else {
                binding.productMemo.setVisibility(View.GONE);
            }

            if (product.isBound()) {
                binding.bindingStatusTitle.setText(com.beaconfinder.app.data.AppLanguage.text("● ビーコン紐付け済"));
                binding.bindingStatusTitle.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.online));
                binding.bindingBeaconId.setText("ID: " + product.beaconCode);
                binding.btnTestLight.setVisibility(View.VISIBLE);
                binding.btnBindBeacon.setText(com.beaconfinder.app.data.AppLanguage.text("変更"));
                binding.btnUnbindBeacon.setVisibility(View.VISIBLE);
            } else {
                binding.bindingStatusTitle.setText(com.beaconfinder.app.data.AppLanguage.text("○ 未バインド"));
                binding.bindingStatusTitle.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.offline));
                binding.bindingBeaconId.setText(com.beaconfinder.app.data.AppLanguage.text("点灯させるビーコンを紐付けてください"));
                binding.btnTestLight.setVisibility(View.GONE);
                binding.btnBindBeacon.setText(com.beaconfinder.app.data.AppLanguage.text("紐付ける"));
                binding.btnUnbindBeacon.setVisibility(View.GONE);
            }

            binding.btnEditProduct.setOnClickListener(v -> listener.onEdit(product));
            binding.btnDeleteProduct.setOnClickListener(v -> listener.onDelete(product));
            binding.btnBindBeacon.setOnClickListener(v -> listener.onBind(product));
            binding.btnUnbindBeacon.setOnClickListener(v -> listener.onUnbind(product));
            binding.btnTestLight.setOnClickListener(v -> listener.onTestLight(product));
        }
    }
}
