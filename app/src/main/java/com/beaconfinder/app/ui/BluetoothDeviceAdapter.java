package com.beaconfinder.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.beaconfinder.app.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder> {

    public static class BleDeviceItem {
        public final String name;
        public final String address;
        public int rssi;

        public BleDeviceItem(String name, String address, int rssi) {
            this.name = name != null && !name.trim().isEmpty() ? name.trim() : "（名称未設定）";
            this.address = address;
            this.rssi = rssi;
        }
    }

    public interface Listener {
        void onDeviceSelected(BleDeviceItem item);
    }

    private final List<BleDeviceItem> items = new ArrayList<>();
    private final Listener listener;

    public BluetoothDeviceAdapter(Listener listener) {
        this.listener = listener;
    }

    public synchronized void addOrUpdate(String name, String address, int rssi) {
        if (address == null) return;
        for (int i = 0; i < items.size(); i++) {
            BleDeviceItem item = items.get(i);
            if (address.equalsIgnoreCase(item.address)) {
                item.rssi = rssi;
                notifyItemChanged(i);
                return;
            }
        }
        items.add(new BleDeviceItem(name, address, rssi));
        // RSSI降順でソート（電波が強いものを上に）
        Collections.sort(items, (a, b) -> Integer.compare(b.rssi, a.rssi));
        notifyDataSetChanged();
    }

    public synchronized void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public synchronized int getCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bluetooth_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BleDeviceItem item = items.get(position);
        holder.tvName.setText(item.name);
        holder.tvAddress.setText(item.address);
        holder.tvRssi.setText(item.rssi + " dBm");

        if (item.rssi >= -65) {
            holder.tvRssi.setTextColor(holder.itemView.getContext().getColor(R.color.online));
        } else if (item.rssi >= -80) {
            holder.tvRssi.setTextColor(holder.itemView.getContext().getColor(R.color.warning));
        } else {
            holder.tvRssi.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }

        holder.btnConnect.setOnClickListener(v -> {
            if (listener != null) listener.onDeviceSelected(item);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDeviceSelected(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvAddress;
        final TextView tvRssi;
        final MaterialButton btnConnect;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvAddress = itemView.findViewById(R.id.tv_device_address);
            tvRssi = itemView.findViewById(R.id.tv_device_rssi);
            btnConnect = itemView.findViewById(R.id.btn_device_connect);
        }
    }
}
