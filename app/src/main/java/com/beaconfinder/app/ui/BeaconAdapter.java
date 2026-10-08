package com.beaconfinder.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.beaconfinder.app.data.WarehouseStore;
import com.beaconfinder.app.databinding.ItemBeaconBinding;
import com.beaconfinder.app.model.Beacon;
import com.beaconfinder.app.model.Product;

import java.util.ArrayList;
import java.util.List;

public final class BeaconAdapter extends RecyclerView.Adapter<BeaconAdapter.Holder> {
    public interface Listener {
        void onLight(Beacon beacon);
        void onDelete(Beacon beacon);
        void onSelectionChanged();
    }

    private final Listener listener;
    private final List<Beacon> items = new ArrayList<>();

    public BeaconAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Beacon> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<Beacon> selected() {
        List<Beacon> result = new ArrayList<>();
        for (Beacon beacon : items) if (beacon.selected) result.add(beacon);
        return result;
    }

    public void selectAll(boolean selected) {
        for (Beacon beacon : items) beacon.selected = selected;
        notifyDataSetChanged();
    }

    public boolean allSelected() {
        if (items.isEmpty()) return false;
        for (Beacon beacon : items) if (!beacon.selected) return false;
        return true;
    }

    public void refresh(Beacon beacon) {
        int index = items.indexOf(beacon);
        if (index >= 0) notifyItemChanged(index);
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBeaconBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override public int getItemCount() { return items.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemBeaconBinding binding;

        Holder(ItemBeaconBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Beacon beacon) {
            binding.beaconName.setText(beacon.name);
            binding.beaconCode.setText("ID " + beacon.code);
            binding.beaconState.setText(com.beaconfinder.app.data.AppLanguage.text(beacon.state));

            WarehouseStore store = WarehouseStore.getInstance(binding.getRoot().getContext());
            Product product = store.getProductByBeaconCode(beacon.code);
            if (product != null) {
                binding.boundProductLayout.setVisibility(View.VISIBLE);
                String skuPart = (product.sku != null && !product.sku.trim().isEmpty()) ? " (" + product.sku + ")" : "";
                binding.boundProductName.setText("📦 " + product.name + skuPart);

                String whName = store.getWarehouseName(product.warehouseId);
                String arName = store.getAreaName(product.areaId);
                StringBuilder loc = new StringBuilder("📍 ");
                if (!whName.isEmpty()) loc.append(whName);
                if (!arName.isEmpty()) {
                    if (!whName.isEmpty()) loc.append(" · ");
                    loc.append(arName);
                }
                if (loc.length() <= 3) loc.append(com.beaconfinder.app.data.AppLanguage.text("位置未設定"));
                binding.boundProductLocation.setText(loc.toString());
            } else {
                binding.boundProductLayout.setVisibility(View.GONE);
            }

            binding.selectedCheckbox.setOnCheckedChangeListener(null);
            binding.selectedCheckbox.setChecked(beacon.selected);
            binding.selectedCheckbox.setOnCheckedChangeListener((button, checked) -> {
                beacon.selected = checked;
                listener.onSelectionChanged();
            });
            binding.getRoot().setOnClickListener(v -> binding.selectedCheckbox.toggle());
            binding.lightButton.setOnClickListener(v -> listener.onLight(beacon));
            binding.deleteButton.setOnClickListener(v -> listener.onDelete(beacon));
        }
    }
}
