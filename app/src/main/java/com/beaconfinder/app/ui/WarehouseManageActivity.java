package com.beaconfinder.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.beaconfinder.app.R;
import com.beaconfinder.app.data.WarehouseStore;
import com.beaconfinder.app.databinding.ActivityWarehouseManageBinding;
import com.beaconfinder.app.model.Area;
import com.beaconfinder.app.model.Warehouse;

import java.util.List;
import java.util.Locale;

public final class WarehouseManageActivity extends AppCompatActivity implements AreaAdapter.Listener {

    @Override protected void onResume() {
        super.onResume();
        if (!getResources().getConfiguration().getLocales().get(0).getLanguage().equals(com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage())) {
            recreate(); return;
        }
        loadWarehouses(currentWarehouse == null ? null : currentWarehouse.id);
    }

    private final android.content.BroadcastReceiver sharedReceiver = new android.content.BroadcastReceiver() {
    @Override public void onReceive(Context context, android.content.Intent intent) {
            loadWarehouses(currentWarehouse == null ? null : currentWarehouse.id);
        }
    };
    @Override protected void onStart() {
        super.onStart();
        androidx.core.content.ContextCompat.registerReceiver(this, sharedReceiver,
            new android.content.IntentFilter(com.beaconfinder.app.data.SharedSync.UPDATED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        com.beaconfinder.app.data.SharedSync.get(this).sync(null);
    }
    @Override protected void onStop() { unregisterReceiver(sharedReceiver); super.onStop(); }

    private ActivityWarehouseManageBinding binding;
    private WarehouseStore store;
    private ArrayAdapter<Warehouse> warehouseAdapter;
    private AreaAdapter areaAdapter;
    private Warehouse currentWarehouse;

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = com.beaconfinder.app.data.AppLanguage.locale(newBase);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityWarehouseManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, statusBars.top, 0, 0);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        store = WarehouseStore.getInstance(this);

        areaAdapter = new AreaAdapter(this);
        binding.areaList.setLayoutManager(new LinearLayoutManager(this));
        binding.areaList.setAdapter(areaAdapter);

        warehouseAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected);
        warehouseAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.warehouseSpinner.setAdapter(warehouseAdapter);

        binding.warehouseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentWarehouse = warehouseAdapter.getItem(position);
                loadAreas();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                currentWarehouse = null;
                loadAreas();
            }
        });

        binding.btnAddWarehouse.setOnClickListener(v -> showAddWarehouseDialog());
        binding.btnEditWarehouse.setOnClickListener(v -> showEditWarehouseDialog());
        binding.btnDeleteWarehouse.setOnClickListener(v -> showDeleteWarehouseDialog());
        binding.btnAddArea.setOnClickListener(v -> showAddAreaDialog());

        loadWarehouses(null);
    }

    private void loadWarehouses(@Nullable String selectId) {
        List<Warehouse> list = store.getWarehouses();
        warehouseAdapter.clear();
        warehouseAdapter.addAll(list);
        warehouseAdapter.notifyDataSetChanged();

        if (list.isEmpty()) {
            currentWarehouse = null;
            binding.btnEditWarehouse.setEnabled(false);
            binding.btnDeleteWarehouse.setEnabled(false);
            binding.btnAddArea.setEnabled(false);
        } else {
            binding.btnEditWarehouse.setEnabled(true);
            binding.btnDeleteWarehouse.setEnabled(true);
            binding.btnAddArea.setEnabled(true);

            int selectedIndex = 0;
            if (selectId != null) {
                for (int i = 0; i < list.size(); i++) {
                    if (selectId.equals(list.get(i).id)) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            binding.warehouseSpinner.setSelection(selectedIndex);
            currentWarehouse = list.get(selectedIndex);
        }
        loadAreas();
    }

    private void loadAreas() {
        if (currentWarehouse == null) {
            areaAdapter.submit(null);
            binding.emptyAreaView.setVisibility(View.VISIBLE);
            binding.emptyAreaView.setText(com.beaconfinder.app.data.AppLanguage.text("倉庫が選択されていません"));
            binding.areaList.setVisibility(View.GONE);
            return;
        }

        List<Area> areas = store.getAreasForWarehouse(currentWarehouse.id);
        areaAdapter.submit(areas);
        boolean empty = areas.isEmpty();
        binding.emptyAreaView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.emptyAreaView.setText("「" + currentWarehouse.name + com.beaconfinder.app.data.AppLanguage.text("」にはエリアが登録されていません。\n「+ エリア追加」から棚や区画を登録してください"));
        binding.areaList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showAddWarehouseDialog() {
        promptInput(com.beaconfinder.app.data.AppLanguage.text("倉庫を追加"), com.beaconfinder.app.data.AppLanguage.text("倉庫名を入力"), "", name -> {
            Warehouse w = new Warehouse();
            w.name = name;
            store.saveWarehouse(w);
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("倉庫「") + name + com.beaconfinder.app.data.AppLanguage.text("」を追加しました"), Toast.LENGTH_SHORT).show();
            loadWarehouses(w.id);
        });
    }

    private void showEditWarehouseDialog() {
        if (currentWarehouse == null) return;
        promptInput(com.beaconfinder.app.data.AppLanguage.text("倉庫名の変更"), com.beaconfinder.app.data.AppLanguage.text("倉庫名を入力"), currentWarehouse.name, name -> {
            currentWarehouse.name = name;
            store.saveWarehouse(currentWarehouse);
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("倉庫名を変更しました"), Toast.LENGTH_SHORT).show();
            loadWarehouses(currentWarehouse.id);
        });
    }

    private void showDeleteWarehouseDialog() {
        if (currentWarehouse == null) return;
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("倉庫の削除"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("倉庫「") + currentWarehouse.name + com.beaconfinder.app.data.AppLanguage.text("」および所属エリアを削除しますか？\n（商品の倉庫所属情報もクリアされます）"))
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("削除"), (d, w) -> {
                    store.deleteWarehouse(currentWarehouse.id);
                    Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("倉庫を削除しました"), Toast.LENGTH_SHORT).show();
                    loadWarehouses(null);
                })
                .show();
    }

    private void showAddAreaDialog() {
        if (currentWarehouse == null) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("先に倉庫を選択または作成してください"), Toast.LENGTH_SHORT).show();
            return;
        }
        promptInput(com.beaconfinder.app.data.AppLanguage.text("エリア（棚・区画）を追加"), com.beaconfinder.app.data.AppLanguage.text("例: A棚-01, 冷凍庫02"), "", name -> {
            Area area = new Area();
            area.warehouseId = currentWarehouse.id;
            area.name = name;
            store.saveArea(area);
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("エリア「") + name + com.beaconfinder.app.data.AppLanguage.text("」を追加しました"), Toast.LENGTH_SHORT).show();
            loadAreas();
        });
    }

    @Override
    public void onEdit(Area area) {
        promptInput(com.beaconfinder.app.data.AppLanguage.text("エリア名の変更"), com.beaconfinder.app.data.AppLanguage.text("エリア名を入力"), area.name, name -> {
            area.name = name;
            store.saveArea(area);
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("エリア名を変更しました"), Toast.LENGTH_SHORT).show();
            loadAreas();
        });
    }

    @Override
    public void onDelete(Area area) {
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("エリアの削除"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("エリア「") + area.name + com.beaconfinder.app.data.AppLanguage.text("」を削除しますか？"))
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("削除"), (d, w) -> {
                    store.deleteArea(area.id);
                    Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("エリアを削除しました"), Toast.LENGTH_SHORT).show();
                    loadAreas();
                })
                .show();
    }

    private interface OnInputConfirmed {
        void onConfirmed(String text);
    }

    private void promptInput(String title, String hint, String defaultValue, OnInputConfirmed callback) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(defaultValue);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setBackgroundResource(R.drawable.bg_config_input);

        int padH = Math.round(16 * getResources().getDisplayMetrics().density);
        int padV = Math.round(10 * getResources().getDisplayMetrics().density);
        input.setPadding(padH, padV, padH, padV);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("保存"), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                input.setError(com.beaconfinder.app.data.AppLanguage.text("名称を入力してください"));
                return;
            }
            callback.onConfirmed(text);
            dialog.dismiss();
        }));

        dialog.show();
    }
}
