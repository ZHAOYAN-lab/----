package com.beaconfinder.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.beaconfinder.app.R;
import com.beaconfinder.app.data.AppConfig;
import com.beaconfinder.app.data.DeviceStore;
import com.beaconfinder.app.data.WarehouseStore;
import com.beaconfinder.app.databinding.ActivityProductManageBinding;
import com.beaconfinder.app.databinding.DialogEditProductBinding;
import com.beaconfinder.app.model.Area;
import com.beaconfinder.app.model.Beacon;
import com.beaconfinder.app.model.Product;
import com.beaconfinder.app.model.Warehouse;
import com.beaconfinder.app.sdk.BaseStationGateway;
import com.beaconfinder.app.sdk.BluetoothStationManager;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ProductManageActivity extends AppCompatActivity implements ProductAdapter.Listener {

    private enum ScanTarget { SKU, BEACON }

    private final android.content.BroadcastReceiver sharedReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, android.content.Intent intent) {
            refreshSharedView();
        }
    };
    @Override protected void onStart() {
        super.onStart();
        androidx.core.content.ContextCompat.registerReceiver(this, sharedReceiver,
            new android.content.IntentFilter(com.beaconfinder.app.data.SharedSync.UPDATED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        com.beaconfinder.app.data.SharedSync.get(this).sync(null);
    }
    @Override protected void onStop() { unregisterReceiver(sharedReceiver); super.onStop(); }

    private ActivityProductManageBinding binding;
    private WarehouseStore warehouseStore;
    private DeviceStore deviceStore;
    private ProductAdapter productAdapter;

    private ScanTarget currentScanTarget = ScanTarget.SKU;
    private EditText activeScanReceiverEditText;

    private String selectedWarehouseId = "";
    private String selectedAreaId = "";

    private final ActivityResultLauncher<ScanOptions> barcodeScanner = registerForActivityResult(
            new ScanContract(), result -> {
                if (result.getContents() == null) return;
                handleScanResult(result.getContents().trim());
            });

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
        binding = ActivityProductManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, statusBars.top, 0, 0);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        warehouseStore = WarehouseStore.getInstance(this);
        deviceStore = DeviceStore.getInstance(this);

        productAdapter = new ProductAdapter(this);
        binding.productList.setLayoutManager(new LinearLayoutManager(this));
        binding.productList.setAdapter(productAdapter);

        setupFilters();
        setupSearch();

        binding.btnAddProductTop.setOnClickListener(v -> showEditProductDialog(null));

        loadProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getResources().getConfiguration().getLocales().get(0).getLanguage().equals(com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage())) {
            recreate(); return;
        }

        refreshSharedView();
    }

    private void refreshSharedView() {
            String warehouseId = selectedWarehouseId;
            setupFilters();
            java.util.List<Warehouse> warehouses = warehouseStore.getWarehouses();
            for (int i = 0; i < warehouses.size(); i++) if (warehouses.get(i).id.equals(warehouseId))
                binding.filterWarehouseSpinner.setSelection(i + 1);
            loadProducts();
    }

    private void setupFilters() {
        List<Warehouse> warehouses = warehouseStore.getWarehouses();
        List<String> whNames = new ArrayList<>();
        whNames.add(com.beaconfinder.app.data.AppLanguage.text("すべての倉庫"));
        for (Warehouse w : warehouses) {
            whNames.add(w.name);
        }

        ArrayAdapter<String> whAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, whNames);
        whAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.filterWarehouseSpinner.setAdapter(whAdapter);

        binding.filterWarehouseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedWarehouseId = "";
                } else {
                    selectedWarehouseId = warehouses.get(position - 1).id;
                }
                updateAreaFilterSpinner();
                loadProducts();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedWarehouseId = "";
                loadProducts();
            }
        });

        updateAreaFilterSpinner();
    }

    private void updateAreaFilterSpinner() {
        List<Area> areas = selectedWarehouseId.isEmpty()
                ? warehouseStore.getAreas()
                : warehouseStore.getAreasForWarehouse(selectedWarehouseId);

        List<String> arNames = new ArrayList<>();
        arNames.add(com.beaconfinder.app.data.AppLanguage.text("すべてのエリア"));
        for (Area a : areas) {
            arNames.add(a.name);
        }

        ArrayAdapter<String> arAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, arNames);
        arAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.filterAreaSpinner.setAdapter(arAdapter);

        binding.filterAreaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedAreaId = "";
                } else {
                    selectedAreaId = areas.get(position - 1).id;
                }
                loadProducts();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedAreaId = "";
                loadProducts();
            }
        });
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                loadProducts();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.searchInput.setText(""));
    }

    private void loadProducts() {
        String query = binding.searchInput.getText().toString().trim();
        List<Product> products = warehouseStore.searchProducts(query, selectedWarehouseId, selectedAreaId);
        productAdapter.submit(products);

        boolean empty = products.isEmpty();
        binding.emptyProductView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.productList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showEditProductDialog(@Nullable Product existingProduct) {
        DialogEditProductBinding dialogBinding = DialogEditProductBinding.inflate(LayoutInflater.from(this));

        List<Warehouse> warehouses = warehouseStore.getWarehouses();
        List<String> whNames = new ArrayList<>();
        whNames.add(com.beaconfinder.app.data.AppLanguage.text("（指定なし）"));
        int initialWhIndex = 0;
        for (int i = 0; i < warehouses.size(); i++) {
            Warehouse w = warehouses.get(i);
            whNames.add(w.name);
            if (existingProduct != null && w.id.equals(existingProduct.warehouseId)) {
                initialWhIndex = i + 1;
            }
        }

        ArrayAdapter<String> whAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, whNames);
        whAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        dialogBinding.spinnerWarehouse.setAdapter(whAdapter);
        dialogBinding.spinnerWarehouse.setSelection(initialWhIndex);

        // Areas adapter
        List<Area> currentAreas = new ArrayList<>();
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected);
        areaAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        dialogBinding.spinnerArea.setAdapter(areaAdapter);

        Runnable refreshDialogAreas = () -> {
            int whPos = dialogBinding.spinnerWarehouse.getSelectedItemPosition();
            currentAreas.clear();
            areaAdapter.clear();
            areaAdapter.add(com.beaconfinder.app.data.AppLanguage.text("（指定なし）"));

            if (whPos > 0) {
                Warehouse selWh = warehouses.get(whPos - 1);
                currentAreas.addAll(warehouseStore.getAreasForWarehouse(selWh.id));
            } else {
                currentAreas.addAll(warehouseStore.getAreas());
            }

            int selectedAreaIdx = 0;
            for (int i = 0; i < currentAreas.size(); i++) {
                Area a = currentAreas.get(i);
                areaAdapter.add(a.name);
                if (existingProduct != null && a.id.equals(existingProduct.areaId)) {
                    selectedAreaIdx = i + 1;
                }
            }
            areaAdapter.notifyDataSetChanged();
            dialogBinding.spinnerArea.setSelection(selectedAreaIdx);
        };

        dialogBinding.spinnerWarehouse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshDialogAreas.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        refreshDialogAreas.run();

        if (existingProduct != null) {
            dialogBinding.inputProductName.setText(existingProduct.name);
            dialogBinding.inputProductSku.setText(existingProduct.sku);
            dialogBinding.inputBeaconCode.setText(existingProduct.beaconCode);
            dialogBinding.inputProductMemo.setText(existingProduct.memo);
            dialogBinding.inputProductShelf.setText(existingProduct.shelf);
        }

        final String[] shelfScope = { existingProduct == null ? "" : existingProduct.areaId };
        dialogBinding.spinnerArea.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedArea = position > 0 && position - 1 < currentAreas.size() ? currentAreas.get(position - 1).id : "";
                if (!selectedArea.equals(shelfScope[0])) dialogBinding.inputProductShelf.setText("");
                shelfScope[0] = selectedArea;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        // Scan SKU button
        dialogBinding.btnScanSku.setOnClickListener(v -> {
            activeScanReceiverEditText = dialogBinding.inputProductSku;
            startScan(ScanTarget.SKU, com.beaconfinder.app.data.AppLanguage.text("商品バーコード（SKU）を読取"));
        });

        // Scan Beacon button
        dialogBinding.btnScanBeacon.setOnClickListener(v -> {
            activeScanReceiverEditText = dialogBinding.inputBeaconCode;
            startScan(ScanTarget.BEACON, com.beaconfinder.app.data.AppLanguage.text("ビーコンQRコード（10桁）を読取"));
        });

        // Pick Beacon from registered
        dialogBinding.btnPickBeacon.setOnClickListener(v -> {
            showPickBeaconDialog(code -> dialogBinding.inputBeaconCode.setText(code));
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existingProduct == null ? com.beaconfinder.app.data.AppLanguage.text("新規商品登録") : com.beaconfinder.app.data.AppLanguage.text("商品情報の編集"))
                .setView(dialogBinding.getRoot())
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("保存"), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = dialogBinding.inputProductName.getText().toString().trim();
            if (name.isEmpty()) {
                dialogBinding.inputProductName.setError(com.beaconfinder.app.data.AppLanguage.text("商品名を入力してください"));
                return;
            }

            String sku = dialogBinding.inputProductSku.getText().toString().trim();
            String beaconCode = dialogBinding.inputBeaconCode.getText().toString().trim();
            if (!beaconCode.isEmpty()) {
                beaconCode = BaseStationGateway.normalizeNumericCode(beaconCode);
            }
            String memo = dialogBinding.inputProductMemo.getText().toString().trim();

            int whPos = dialogBinding.spinnerWarehouse.getSelectedItemPosition();
            String whId = (whPos > 0 && whPos - 1 < warehouses.size()) ? warehouses.get(whPos - 1).id : "";

            int arPos = dialogBinding.spinnerArea.getSelectedItemPosition();
            String arId = (arPos > 0 && arPos - 1 < currentAreas.size()) ? currentAreas.get(arPos - 1).id : "";

            String shelf = dialogBinding.inputProductShelf.getText().toString().trim();
            if (!shelf.isEmpty() && arId.isEmpty()) { dialogBinding.inputProductShelf.setError(com.beaconfinder.app.data.AppLanguage.text("绑定货架前请先选择所属区域")); return; }
            if (!arId.isEmpty()) whId = currentAreas.get(arPos - 1).warehouseId;
            Product prod = (existingProduct != null) ? existingProduct : new Product();
            prod.name = name;
            prod.sku = sku;
            prod.warehouseId = whId;
            prod.areaId = arId;
            prod.shelf = shelf;
            prod.beaconCode = beaconCode;
            prod.memo = memo;

            warehouseStore.saveProduct(prod);
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("商品「") + name + com.beaconfinder.app.data.AppLanguage.text("」を保存しました"), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            loadProducts();
        }));

        dialog.show();
    }

    private void showPickBeaconDialog(OnBeaconPicked callback) {
        List<Beacon> beacons = deviceStore.beacons();
        if (beacons.isEmpty()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("登録されたビーコンがありません。先にメイン画面でビーコンを追加してください"), Toast.LENGTH_LONG).show();
            return;
        }

        String[] items = new String[beacons.size()];
        for (int i = 0; i < beacons.size(); i++) {
            Beacon b = beacons.get(i);
            items[i] = b.name + " (" + b.code + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("登録済みビーコンから選択"))
                .setItems(items, (dialog, which) -> {
                    callback.onPicked(beacons.get(which).code);
                })
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .show();
    }

    private interface OnBeaconPicked {
        void onPicked(String code);
    }

    private void startScan(ScanTarget target, String prompt) {
        currentScanTarget = target;
        ScanOptions options = new ScanOptions()
                .setCaptureActivity(CustomScannerActivity.class)
                .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                .setPrompt(prompt)
                .setBeepEnabled(true)
                .setOrientationLocked(true)
                .addExtra("SCAN_TITLE", prompt)
                .addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
        barcodeScanner.launch(options);
    }

    private void handleScanResult(String raw) {
        if (activeScanReceiverEditText == null) return;
        if (currentScanTarget == ScanTarget.BEACON) {
            try {
                String code = raw.replaceAll("[^0-9]", "");
                if (code.length() > 10) code = code.substring(code.length() - 10);
                activeScanReceiverEditText.setText(BaseStationGateway.normalizeNumericCode(code));
            } catch (Exception e) {
                activeScanReceiverEditText.setText(raw);
            }
        } else {
            activeScanReceiverEditText.setText(raw);
        }
    }

    @Override
    public void onEdit(Product product) {
        showEditProductDialog(product);
    }

    @Override
    public void onDelete(Product product) {
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("商品の削除"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("商品「") + product.name + com.beaconfinder.app.data.AppLanguage.text("」を削除しますか？"))
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("削除"), (d, w) -> {
                    warehouseStore.deleteProduct(product.id);
                    Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("商品を削除しました"), Toast.LENGTH_SHORT).show();
                    loadProducts();
                })
                .show();
    }

    @Override
    public void onBind(Product product) {
        // Quick bind dialog
        EditText input = new EditText(this);
        input.setHint(com.beaconfinder.app.data.AppLanguage.text("10桁のビーコンIDを入力"));
        if (product.beaconCode != null && !product.beaconCode.isEmpty()) {
            input.setText(product.beaconCode);
        }
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setBackgroundResource(R.drawable.bg_config_input);

        int padH = Math.round(16 * getResources().getDisplayMetrics().density);
        int padV = Math.round(10 * getResources().getDisplayMetrics().density);
        input.setPadding(padH, padV, padH, padV);

        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("ビーコンの紐付け: ") + product.name)
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("紐付けるビーコンの10桁IDを入力、または一覧から選択してください"))
                .setView(input)
                .setNeutralButton(com.beaconfinder.app.data.AppLanguage.text("一覧から選択"), (d, w) -> {
                    showPickBeaconDialog(code -> {
                        warehouseStore.bindProductToBeacon(product.id, code);
                        Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + code + com.beaconfinder.app.data.AppLanguage.text(" を紐付けました"), Toast.LENGTH_SHORT).show();
                        loadProducts();
                    });
                })
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("紐付け保存"), (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (!code.isEmpty()) {
                        code = BaseStationGateway.normalizeNumericCode(code);
                    }
                    warehouseStore.bindProductToBeacon(product.id, code);
                    Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("ビーコン紐付けを保存しました"), Toast.LENGTH_SHORT).show();
                    loadProducts();
                })
                .show();
    }

    @Override
    public void onUnbind(Product product) {
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("ビーコン紐付け解除"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("商品「") + product.name + com.beaconfinder.app.data.AppLanguage.text("」のビーコン紐付け (") + product.beaconCode + com.beaconfinder.app.data.AppLanguage.text(") を解除しますか？"))
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("解除"), (d, w) -> {
                    warehouseStore.unbindProduct(product.id);
                    Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("紐付けを解除しました"), Toast.LENGTH_SHORT).show();
                    loadProducts();
                })
                .show();
    }

    @Override
    public void onTestLight(Product product) {
        if (com.beaconfinder.app.data.SharedSync.get(this).configured() && product.isBound()) {
            java.util.List<com.beaconfinder.app.model.Beacon> matches = new java.util.ArrayList<>();
            for (com.beaconfinder.app.model.Beacon b : deviceStore.beacons()) if (product.beaconCode.equals(b.code)) matches.add(b);
            if (matches.size() != 1) {
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("请先登记信标所属基站；多个基站时请在主页面选择对应信标"), Toast.LENGTH_LONG).show();
                return;
            }
            com.beaconfinder.app.sdk.WarehouseBridgeService.start(this);
            AppConfig cfg = AppConfig.getInstance(this);
            com.beaconfinder.app.data.SharedSync.get(this).find(matches, false, cfg.selectedColor, cfg.flashPrompt, cfg.soundPrompt);
            return;
        }
        if (!product.isBound()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("ビーコンが紐付いていません"), Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothStationManager bleMgr = BluetoothStationManager.getInstance(this);
        AppConfig config = AppConfig.getInstance(this);

        if (bleMgr.isConnected()) {
            String result = bleMgr.light(
                    Collections.singletonList(product.beaconCode),
                    config.selectedColor,
                    config.flashPrompt,
                    config.soundPrompt,
                    config.lightDurationSec,
                    config.flashIntervalSec
            );
            if ("0".equals(result)) {
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("💡 ビーコン ") + product.beaconCode + com.beaconfinder.app.data.AppLanguage.text(" に点灯指示を送信しました"), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("点灯指示失敗 (エラーコード: ") + result + ")", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("Bluetoothロケーターに未接続です。メイン画面で接続を確認してください"), Toast.LENGTH_LONG).show();
        }
    }
}
