package com.beaconfinder.app.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.beaconfinder.app.databinding.ItemAreaBinding;
import com.beaconfinder.app.model.Area;

import java.util.ArrayList;
import java.util.List;

public final class AreaAdapter extends RecyclerView.Adapter<AreaAdapter.Holder> {
    public interface Listener {
        void onEdit(Area area);
        void onDelete(Area area);
    }

    private final Listener listener;
    private final List<Area> items = new ArrayList<>();

    public AreaAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Area> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemAreaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemAreaBinding binding;

        Holder(ItemAreaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Area area) {
            binding.areaName.setText(area.name);
            binding.areaSubText.setText("ID: " + area.id);
            binding.btnEditArea.setOnClickListener(v -> listener.onEdit(area));
            binding.btnDeleteArea.setOnClickListener(v -> listener.onDelete(area));
        }
    }
}
