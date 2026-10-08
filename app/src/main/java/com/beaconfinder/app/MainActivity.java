package com.beaconfinder.app;

import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import com.beaconfinder.app.data.AppConfig;
import com.beaconfinder.app.data.DeviceStore;
import com.beaconfinder.app.data.WarehouseStore;
import com.beaconfinder.app.data.SharedSync;
import com.beaconfinder.app.sdk.WarehouseBridgeService;
import com.beaconfinder.app.ui.WarehouseMapActivity;
import com.beaconfinder.app.databinding.ActivityMainBinding;
import com.beaconfinder.app.model.Area;
import com.beaconfinder.app.model.BaseStation;
import com.beaconfinder.app.model.Beacon;
import com.beaconfinder.app.model.Product;
import com.beaconfinder.app.model.Warehouse;
import com.beaconfinder.app.sdk.BaseStationGateway;
import com.beaconfinder.app.sdk.BluetoothStationManager;
import com.beaconfinder.app.ui.BeaconAdapter;
import com.beaconfinder.app.ui.ConfigActivity;
import com.beaconfinder.app.ui.CustomScannerActivity;
import com.beaconfinder.app.ui.ProductManageActivity;
import com.beaconfinder.app.ui.WarehouseManageActivity;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends AppCompatActivity
        implements BaseStationGateway.Listener, BeaconAdapter.Listener {

    private final android.content.BroadcastReceiver sharedReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshMainFilters();
            baseAdapter.notifyDataSetChanged();
            showCurrentBaseBeacons();
        }
    };
    @Override protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, sharedReceiver, new android.content.IntentFilter(SharedSync.UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED);
        SharedSync.get(this).sync(null);
    }
    @Override protected void onStop() {
        unregisterReceiver(sharedReceiver);
        super.onStop();
    }

    private static final String TAG = "MainActivity";
    private enum ScanPurpose { BASE, BEACON }

    private ActivityMainBinding binding;
    private DeviceStore store;
    private WarehouseStore warehouseStore;
    private BaseStationGateway gateway;
    private BeaconAdapter beaconAdapter;
    private ArrayAdapter<BaseStation> baseAdapter;
    private ScanPurpose scanPurpose = ScanPurpose.BEACON;
    private boolean online;
    private String onlineSn = "";
    private boolean suppressSelectAllCallback;
    private boolean suppressConfigUpdate = false;
    private boolean userDisconnected = true;

    private String mainSearchQuery = "";
    private String mainFilterWarehouseId = "";
    private String mainFilterAreaId = "";
    private ArrayAdapter<String> mainWarehouseFilterAdapter;
    private ArrayAdapter<String> mainAreaFilterAdapter;
    private final List<Warehouse> mainFilterWarehouses = new ArrayList<>();
    private final List<Area> mainFilterAreas = new ArrayList<>();

    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable connectTimeoutRunnable;

    // LAN 内で検出されたロケーターリスト（キャッシュ）
    public static class DiscoveredBase {
        public final String id;
        public final String ip;
        public final String port;
        public final String mac;
        public final boolean appOk;

        public DiscoveredBase(String id, String ip, String port, String mac, boolean appOk) {
            this.id = id;
            this.ip = ip;
            this.port = port;
            this.mac = mac;
            this.appOk = appOk;
        }
    }
    private final Map<String, DiscoveredBase> discoveredBases = Collections.synchronizedMap(new LinkedHashMap<>());

    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(
            new ScanContract(), result -> {
                if (result.getContents() == null) return;
                handleScan(result.getContents());
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = com.beaconfinder.app.data.AppLanguage.locale(newBase);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ステータスバーとの重なりを防止（WindowInsets を AppBar に適用）
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, statusBars.top, 0, 0);
            return insets;
        });

        acquireWifiLocks();

        store = DeviceStore.getInstance(this);
        warehouseStore = WarehouseStore.getInstance(this);
        gateway = BaseStationGateway.getInstance();
        gateway.setListener(this);
        beaconAdapter = new BeaconAdapter(this);

        binding.beaconList.setLayoutManager(new LinearLayoutManager(this));
        binding.beaconList.setAdapter(beaconAdapter);

        baseAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, store.bases());
        baseAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.baseSpinner.setAdapter(baseAdapter);

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(this,
                R.layout.item_spinner_selected,
                new String[] {com.beaconfinder.app.data.AppLanguage.text("赤"), com.beaconfinder.app.data.AppLanguage.text("黄"), com.beaconfinder.app.data.AppLanguage.text("青"), com.beaconfinder.app.data.AppLanguage.text("緑"), com.beaconfinder.app.data.AppLanguage.text("シアン"), com.beaconfinder.app.data.AppLanguage.text("白"), com.beaconfinder.app.data.AppLanguage.text("紫")});
        colorAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.colorSpinner.setAdapter(colorAdapter);

        // 設定から現在の色・音声・フラッシュ状態を読み込んでメイン画面に反映
        AppConfig initialConfig = AppConfig.getInstance(this);
        suppressConfigUpdate = true;
        binding.colorSpinner.setSelection(colorCodeToIndex(initialConfig.selectedColor));
        binding.beepSwitch.setChecked(initialConfig.soundPrompt);
        binding.flashSwitch.setChecked(initialConfig.flashPrompt);
        suppressConfigUpdate = false;

        // メイン画面での変更を即時反映 & 保存
        binding.colorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (suppressConfigUpdate) return;
                AppConfig cfg = AppConfig.getInstance(MainActivity.this);
                cfg.selectedColor = indexToColorCode(position);
                cfg.save(MainActivity.this);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        binding.beepSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressConfigUpdate) return;
            AppConfig cfg = AppConfig.getInstance(this);
            cfg.soundPrompt = isChecked;
            cfg.save(this);
        });
        binding.flashSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressConfigUpdate) return;
            AppConfig cfg = AppConfig.getInstance(this);
            cfg.flashPrompt = isChecked;
            cfg.save(this);
        });

        binding.baseSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(this::showCurrentBaseBeacons));
        binding.scanBaseButton.setOnClickListener(v -> startScan(ScanPurpose.BASE));
        binding.searchLanButton.setOnClickListener(v -> showSearchLanDialog());
        binding.scanBeaconButton.setOnClickListener(v -> {
            ensureActiveBaseStation();
            startScan(ScanPurpose.BEACON);
        });
        binding.connectButton.setOnClickListener(v -> toggleConnection());
        binding.deleteBaseButton.setOnClickListener(v -> confirmDeleteBase());
        binding.selectAllCheckbox.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSelectAllCallback) return;
            beaconAdapter.selectAll(checked);
            updateSelectionStatus();
        });
        binding.lightSelectedButton.setOnClickListener(v -> controlSelected(false));
        binding.stopSelectedButton.setOnClickListener(v -> controlSelected(true));

        binding.btnOpenWarehouse.setOnClickListener(v -> {
            startActivity(new Intent(this, WarehouseManageActivity.class));
        });
        binding.btnOpenMap.setOnClickListener(v -> { startActivity(new Intent(this, WarehouseMapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish(); });
        WarehouseBridgeService.start(this);
        binding.btnOpenProducts.setOnClickListener(v -> {
            startActivity(new Intent(this, ProductManageActivity.class));
        });
        binding.btnOpenConfig.setOnClickListener(v -> {
            startActivity(new Intent(this, ConfigActivity.class));
        });
        binding.bluetoothBaseButton.setOnClickListener(v -> {
            startActivity(new Intent(this, ConfigActivity.class));
        });

        setupMainSearchAndFilters();

        showCurrentBaseBeacons();
        binding.operationStatus.setText("SDK v" + gateway.version() + com.beaconfinder.app.data.AppLanguage.text(" · 準備完了"));

        // 起動時にBLE/MINIロケーターを自動検索・接続
        checkAndRequestBlePermissions();

        // 起動時にLAN内のロケーターを事前探索
        gateway.triggerSubnetBroadcast();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getResources().getConfiguration().getLocales().get(0).getLanguage().equals(com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage())) {
            recreate(); return;
        }

        refreshMainFilters();
        baseAdapter.notifyDataSetChanged();
        showCurrentBaseBeacons();

        // 設定画面で変更された色・音声・フラッシュ設定をメイン画面のUIに同期
        AppConfig config = AppConfig.getInstance(this);
        suppressConfigUpdate = true;
        binding.colorSpinner.setSelection(colorCodeToIndex(config.selectedColor));
        binding.beepSwitch.setChecked(config.soundPrompt);
        binding.flashSwitch.setChecked(config.flashPrompt);
        suppressConfigUpdate = false;

        BluetoothStationManager bleMgr = BluetoothStationManager.getInstance(this);
        if (bleMgr.isConnected()) {
            binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● BLEロケーター: 接続完了"));
            binding.connectionStatus.setTextColor(getColor(R.color.online));
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("BLEロケーターに接続中です。ビーコンのバーコードをスキャンして点灯できます"));
        } else {
            if (config.isBluetoothBase || config.isMiniBase) {
                checkAndRequestBlePermissions();
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // BLE接続フロー (公式Demo: checkGpsEnabled → checkBlePermissions →
    //               checkBleEnabled → checkGpsPermission → connect("ble",""))
    // ────────────────────────────────────────────────────────────────────────

    private void checkAndRequestBlePermissions() {
        // Step1: GPS(位置情報)が有効か確認 (Android BLEスキャンの必須要件)
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gpsEnabled = lm != null && (
                lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));

        if (!gpsEnabled) {
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("⚠ BLE接続には「位置情報」のONが必要です。設定→位置情報をONにしてください"));
            binding.operationStatus.setTextColor(getColor(R.color.warning));
            // 設定画面を開くよう促す
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(com.beaconfinder.app.data.AppLanguage.text("位置情報をONにしてください"))
                    .setMessage(com.beaconfinder.app.data.AppLanguage.text("Bluetoothロケーターのスキャンには、Androidの「位置情報（GPS）」が有効になっている必要があります。\n\n設定→位置情報 をONにしてからアプリを再起動してください。"))
                    .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("設定を開く"), (d, w) -> {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("後で"), null)
                    .show();
            return;
        }

        // Step2: BLE権限を確認
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                }, 101);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, 101);
                return;
            }
        }

        // Step3: Bluetoothが有効か確認
        android.bluetooth.BluetoothAdapter btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("⚠ Bluetoothが無効です。ONにしてください"));
            startActivity(new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        // Step4: 全条件OK → BLE接続開始
        autoConnectBluetoothBase();
    }

    private void autoConnectBluetoothBase() {
        AppConfig config = AppConfig.getInstance(this);
        if (!config.isBluetoothBase && !config.isMiniBase) return;
        BluetoothStationManager bleMgr = BluetoothStationManager.getInstance(this);
        bleMgr.setListener(new BluetoothStationManager.Listener() {
            @Override
            public void onBleStateChanged(int state, int rssi, String stateDesc) {
                runOnUiThread(() -> {
                    switch (state) {
                        case feasyblue.BleStateTypes.Connected:
                            binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● BLEロケーター: 接続完了 (") + rssi + " dBm)");
                            binding.connectionStatus.setTextColor(getColor(R.color.online));
                            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("BLEロケーターに接続しました。ビーコンのバーコードをスキャンして点灯できます"));
                            break;
                        case feasyblue.BleStateTypes.Scaning:
                            binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● BLE: スキャン中 (XY-FlashFind)…"));
                            binding.connectionStatus.setTextColor(getColor(R.color.warning));
                            break;
                        case feasyblue.BleStateTypes.Connecting:
                            binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● BLE: 接続試行中…"));
                            binding.connectionStatus.setTextColor(getColor(R.color.warning));
                            break;
                        default:
                            binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● BLE: 未接続"));
                            binding.connectionStatus.setTextColor(getColor(R.color.offline));
                            break;
                    }
                });
            }
            @Override public void onDeviceFound(String name, String address, int rssi) {}
            @Override public void onMessageReceived(String data) {}
            @Override public void onLog(String message) {
                Log.d("MainActivity", "BLE: " + message);
            }
        });

        if (!bleMgr.isConnected()) {
            int filter = config.signalFilterRssi > 0 ? -config.signalFilterRssi : -90;
            bleMgr.autoConnect(filter);
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("BLEロケーター (XY-FlashFind) を検索中…"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                autoConnectBluetoothBase();
            } else {
                toast(com.beaconfinder.app.data.AppLanguage.text("Bluetooth/位置情報の権限が必要です。設定→アプリから許可してください"));
            }
        }
    }



    private BaseStation ensureActiveBaseStation() {
        BaseStation base = currentBase();
        if (base == null) {
            BaseStation defaultBle = new BaseStation("XY-FlashFind", com.beaconfinder.app.data.AppLanguage.text("Bluetoothロケーター (Sanray)"));
            store.addBase(defaultBle);
            refreshBasesAndSelect(defaultBle.sn);
            return defaultBle;
        }
        return base;
    }

    private void acquireWifiLocks() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (multicastLock == null) {
                    multicastLock = wm.createMulticastLock("beacon_finder_multicast");
                    multicastLock.setReferenceCounted(true);
                }
                if (!multicastLock.isHeld()) {
                    multicastLock.acquire();
                }
                if (wifiLock == null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "beacon_finder_wifi");
                    wifiLock.setReferenceCounted(true);
                }
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire wifi/multicast lock", e);
        }
    }

    private void releaseWifiLocks() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {}
    }

    private void startScan(ScanPurpose purpose) {
        acquireWifiLocks();
        scanPurpose = purpose;
        ScanOptions options = new ScanOptions()
                .setCaptureActivity(CustomScannerActivity.class)
                .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                .setPrompt(purpose == ScanPurpose.BASE
                        ? getString(R.string.scan_base_prompt)
                        : getString(R.string.scan_beacon_prompt))
                .setBeepEnabled(true)
                .setOrientationLocked(true)
                .addExtra("SCAN_TITLE", purpose == ScanPurpose.BASE
                        ? getString(R.string.scan_base_title)
                        : getString(R.string.scan_beacon_title))
                .addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
        scanner.launch(options);
    }

    private void handleScan(String raw) {
        try {
            if (scanPurpose == ScanPurpose.BASE) {
                BaseInfo info = parseBaseInfo(raw);
                String defaultName = com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + (store.bases().size() + 1);
                promptName(com.beaconfinder.app.data.AppLanguage.text("ロケーターを追加"), defaultName, name -> {
                    store.addBase(new BaseStation(info.sn, name));
                    refreshBasesAndSelect(info.sn);
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + info.sn + com.beaconfinder.app.data.AppLanguage.text(" を追加しました。接続中…"));
                    connectCurrentBase();
                });
            } else {
                BaseStation base = ensureActiveBaseStation();
                String code = parseBeaconCode(raw);
                String suffix = code.length() >= 4 ? code.substring(code.length() - 4) : code;
                AppConfig config = AppConfig.getInstance(this);

                if (config.fastBind) {
                    Beacon beacon = new Beacon(code, com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + suffix, base.sn);
                    store.addBeacon(beacon);
                    showCurrentBaseBeacons();
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + code + com.beaconfinder.app.data.AppLanguage.text(" 高速バインド完了、点灯中…"));
                    int delay = Math.max(50, config.bindDelayMs);
                    mainHandler.postDelayed(() -> {
                        controlBeaconWithConfig(beacon);
                    }, delay);
                } else {
                    promptName(com.beaconfinder.app.data.AppLanguage.text("ビーコンを追加"), com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + suffix, name -> {
                        Beacon beacon = new Beacon(code, name, base.sn);
                        store.addBeacon(beacon);
                        showCurrentBaseBeacons();
                        controlBeaconWithConfig(beacon);
                    });
                }
            }
        } catch (Exception error) {
            new AlertDialog.Builder(this)
                    .setTitle(com.beaconfinder.app.data.AppLanguage.text("QRコードの解析エラー"))
                    .setMessage(error.getMessage() + com.beaconfinder.app.data.AppLanguage.text("\n\nスキャン内容: ") + raw)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    public static class BaseInfo {
        public final String raw;
        public final String sn;
        public final String ip;
        public final int port;
        public final String mac;

        public BaseInfo(String raw, String sn, String ip, int port, String mac) {
            this.raw = raw;
            this.sn = sn;
            this.ip = ip;
            this.port = port;
            this.mac = mac;
        }
    }

    private BaseInfo parseBaseInfo(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(com.beaconfinder.app.data.AppLanguage.text("QRコードの内容が空です"));
        }
        String trimmed = raw.trim().replace("\uFEFF", "").replaceAll("^[\"']+|[\"']+$", "").trim();

        // 1. JSON 形式の解析
        if (trimmed.contains("{") && trimmed.contains("}")) {
            try {
                int start = trimmed.indexOf('{');
                int end = trimmed.lastIndexOf('}') + 1;
                JSONObject obj = new JSONObject(trimmed.substring(start, end));

                String sn = firstJsonValue(obj, "sn", "SN", "id", "ID", "deviceId", "device_id", "deviceSN", "baseSn", "code");
                String ip = firstJsonValue(obj, "ip", "IP", "ri", "Ri", "host", "addr");
                String portStr = firstJsonValue(obj, "port", "Port", "rp", "Rp");
                String mac = firstJsonValue(obj, "mac", "Mac", "MAC");
                int port = 5000;
                if (portStr != null) {
                    try { port = Integer.parseInt(portStr.trim()); } catch (Exception ignored) {}
                }

                if (sn != null || ip != null || mac != null) {
                    String finalSn = sn != null ? sn.trim() : (ip != null ? ip.trim() : mac.trim());
                    return new BaseInfo(raw, finalSn, ip != null ? ip.trim() : "", port, mac != null ? mac.trim() : "");
                }
            } catch (Exception ignored) {}
        }

        // 2. URL 形式の解析
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("http://") ||
            trimmed.toLowerCase(Locale.ROOT).startsWith("https://") ||
            trimmed.toLowerCase(Locale.ROOT).startsWith("tcp://")) {
            try {
                java.net.URI uri = new java.net.URI(trimmed);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5000;
                if (host != null && !host.isEmpty()) {
                    return new BaseInfo(raw, host, host, port, "");
                }
            } catch (Exception ignored) {}
        }

        // 3. プレフィックスの除去 (BASE:, STATION:, SN:, ID:, MAC:, IP:, DEV:, etc.)
        String stripped = trimmed.replaceFirst("(?i)^(BASE|STATION|BASE_SN|SN|S/N|ID|MAC|IP|DEV|DEVICE|NO)\\s*[:=_-]\\s*", "").trim();
        stripped = stripped.replaceAll("^[\"']+|[\"']+$", "").trim();

        // 4. 10桁の数字（ロケーターの10桁バーコード、シリアル番号）を最優先で明確に判定
        if (stripped.matches("^\\d{10}$")) {
            return new BaseInfo(raw, stripped, "", 5000, "");
        }

        // 5. IP アドレス形式 (192.168.1.50 または 192.168.1.50:5000)
        if (stripped.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?$")) {
            String ip = stripped;
            int port = 5000;
            if (stripped.contains(":")) {
                String[] p = stripped.split(":");
                ip = p[0];
                try { port = Integer.parseInt(p[1]); } catch (Exception ignored) {}
            }
            return new BaseInfo(raw, stripped, ip, port, "");
        }

        // 6. MAC アドレス形式
        if (stripped.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$") ||
            (stripped.length() == 12 && stripped.matches("^[0-9A-Fa-f]{12}$"))) {
            return new BaseInfo(raw, stripped, "", 5000, stripped);
        }

        // 7. 一般的な英数字識別子 (3～64文字の英数字、ハイフン、アンダースコア、ドット)
        String cleaned = stripped.replaceAll("[^A-Za-z0-9_.-]", "");
        if (cleaned.length() >= 3 && cleaned.length() <= 64) {
            return new BaseInfo(raw, cleaned, "", 5000, "");
        }

        throw new IllegalArgumentException(com.beaconfinder.app.data.AppLanguage.text("ロケーターのSN、IP、またはMACアドレスを認識できませんでした。\n\n内容: ") + raw);
    }

    private String parseBeaconCode(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(com.beaconfinder.app.data.AppLanguage.text("QRコードの内容が空です"));
        }
        String trimmed = raw.trim().replace("\uFEFF", "").replaceAll("^[\"']+|[\"']+$", "").trim();

        if (trimmed.contains("{") && trimmed.contains("}")) {
            try {
                int start = trimmed.indexOf('{');
                int end = trimmed.lastIndexOf('}') + 1;
                JSONObject obj = new JSONObject(trimmed.substring(start, end));
                String val = firstJsonValue(obj, "code", "id", "ID", "tag", "beacon", "sn");
                if (val != null) trimmed = val.trim();
            } catch (Exception ignored) {}
        }

        String value = trimmed.replaceFirst("(?i)^(TAG|BEACON|ID|CODE|SN)\\s*[:=_-]\\s*", "").replaceAll("[\\s-]", "");
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException(com.beaconfinder.app.data.AppLanguage.text("ビーコンの10進数コードが見つかりません。\n\n内容: ") + raw);
        }
        if (value.length() > 10) value = value.substring(value.length() - 10);
        return BaseStationGateway.normalizeNumericCode(value);
    }

    private static String firstJsonValue(JSONObject obj, String... keys) {
        for (String key : keys) {
            String val = obj.optString(key, "").trim();
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    private interface NameReceiver { void receive(String name); }

    private void promptName(String title, String defaultName, NameReceiver receiver) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(defaultName);
        input.setSelectAllOnFocus(true);
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setBackgroundResource(R.drawable.bg_config_input);
        int paddingH = Math.round(16 * getResources().getDisplayMetrics().density);
        int paddingV = Math.round(10 * getResources().getDisplayMetrics().density);
        input.setPadding(paddingH, paddingV, paddingH, paddingV);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("保存"), null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(com.beaconfinder.app.data.AppLanguage.text("名称を入力してください"));
                return;
            }
            receiver.receive(name);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showSearchLanDialog() {
        acquireWifiLocks();
        gateway.triggerSubnetBroadcast();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView infoText = new TextView(this);
        infoText.setText(com.beaconfinder.app.data.AppLanguage.text("LAN内のロケーターを探索しています (UDPポート9001/9002)…\n検出されたロケーターをタップすると即座に追加・接続されます。"));
        infoText.setTextColor(Color.parseColor("#334155"));
        infoText.setPadding(0, 0, 0, padding / 2);
        layout.addView(infoText);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(listContainer);
        layout.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("LAN内ロケーター検索 / 手動入力"))
                .setView(layout)
                .setNeutralButton(com.beaconfinder.app.data.AppLanguage.text("手動入力"), (d, w) -> showManualInputDialog())
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("閉じる"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("再検索"), null)
                .create();

        Runnable refreshList = () -> {
            listContainer.removeAllViews();
            synchronized (discoveredBases) {
                if (discoveredBases.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText(com.beaconfinder.app.data.AppLanguage.text("（現在検出されているロケーターはありません。同一Wi-Fiに接続されているか確認してください）"));
                    empty.setPadding(0, padding / 2, 0, padding / 2);
                    empty.setTextColor(Color.parseColor("#64748B"));
                    listContainer.addView(empty);
                } else {
                    for (DiscoveredBase base : discoveredBases.values()) {
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.VERTICAL);
                        row.setPadding(0, padding / 2, 0, padding / 2);
                        row.setBackgroundResource(android.R.drawable.list_selector_background);

                        TextView title = new TextView(this);
                        title.setText("SN: " + base.id + " (" + (base.appOk ? com.beaconfinder.app.data.AppLanguage.text("接続可能") : com.beaconfinder.app.data.AppLanguage.text("他クライアント接続中")) + ")");
                        title.setTextSize(16);
                        title.setTextColor(base.appOk ? Color.parseColor("#047857") : Color.RED);
                        title.getPaint().setFakeBoldText(true);
                        row.addView(title);

                        TextView details = new TextView(this);
                        details.setText("IP: " + base.ip + ":" + base.port + " · MAC: " + base.mac);
                        details.setTextSize(13);
                        details.setTextColor(Color.parseColor("#334155"));
                        row.addView(details);

                        row.setOnClickListener(v -> {
                            dialog.dismiss();
                            promptName(com.beaconfinder.app.data.AppLanguage.text("ロケーターを追加"), com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + (store.bases().size() + 1), name -> {
                                store.addBase(new BaseStation(base.id, name));
                                refreshBasesAndSelect(base.id);
                                binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + base.id + com.beaconfinder.app.data.AppLanguage.text(" を選択しました"));
                                connectCurrentBase();
                            });
                        });
                        listContainer.addView(row);
                    }
                }
            }
        };

        refreshList.run();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                gateway.triggerSubnetBroadcast();
                toast(com.beaconfinder.app.data.AppLanguage.text("再検索パケットを送信しました"));
                mainHandler.postDelayed(refreshList, 800);
            });
        });
        dialog.show();

        // 1秒後と2秒後にリストを自動更新
        mainHandler.postDelayed(refreshList, 1000);
        mainHandler.postDelayed(refreshList, 2500);
    }

    private void showManualInputDialog() {
        EditText input = new EditText(this);
        input.setHint(com.beaconfinder.app.data.AppLanguage.text("例: 192.168.1.50 または QJ000000000001"));
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setBackgroundResource(R.drawable.bg_config_input);
        int paddingH = Math.round(16 * getResources().getDisplayMetrics().density);
        int paddingV = Math.round(10 * getResources().getDisplayMetrics().density);
        input.setPadding(paddingH, paddingV, paddingH, paddingV);

        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("IPアドレスまたはSNの手動入力"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("ロケーターのIPアドレスまたはSNを入力してください。"))
                .setView(input)
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("追加して接続"), (d, which) -> {
                    String val = input.getText().toString().trim();
                    if (val.isEmpty()) {
                        toast(com.beaconfinder.app.data.AppLanguage.text("入力内容が空です"));
                        return;
                    }
                    promptName(com.beaconfinder.app.data.AppLanguage.text("ロケーターの名称"), com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + (store.bases().size() + 1), name -> {
                        store.addBase(new BaseStation(val, name));
                        refreshBasesAndSelect(val);
                        connectCurrentBase();
                    });
                }).show();
    }

    private void refreshBasesAndSelect(String sn) {
        baseAdapter.notifyDataSetChanged();
        for (int i = 0; i < store.bases().size(); i++) {
            if (store.bases().get(i).sn.equalsIgnoreCase(sn)) {
                binding.baseSpinner.setSelection(i);
                break;
            }
        }
        showCurrentBaseBeacons();
    }

    private BaseStation currentBase() {
        Object item = binding.baseSpinner.getSelectedItem();
        return item instanceof BaseStation ? (BaseStation) item : null;
    }

    private void setupMainSearchAndFilters() {
        binding.mainSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mainSearchQuery = s.toString().trim();
                binding.btnClearMainSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                showCurrentBaseBeacons();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearMainSearch.setOnClickListener(v -> binding.mainSearchInput.setText(""));

        mainWarehouseFilterAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected);
        mainWarehouseFilterAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.mainFilterWarehouseSpinner.setAdapter(mainWarehouseFilterAdapter);

        mainAreaFilterAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected);
        mainAreaFilterAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.mainFilterAreaSpinner.setAdapter(mainAreaFilterAdapter);

        binding.mainFilterWarehouseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mainFilterWarehouseId = "";
                } else if (position - 1 < mainFilterWarehouses.size()) {
                    mainFilterWarehouseId = mainFilterWarehouses.get(position - 1).id;
                }
                refreshMainAreaFilterSpinner();
                showCurrentBaseBeacons();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mainFilterWarehouseId = "";
                showCurrentBaseBeacons();
            }
        });

        binding.mainFilterAreaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mainFilterAreaId = "";
                } else if (position - 1 < mainFilterAreas.size()) {
                    mainFilterAreaId = mainFilterAreas.get(position - 1).id;
                }
                showCurrentBaseBeacons();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mainFilterAreaId = "";
                showCurrentBaseBeacons();
            }
        });

        refreshMainFilters();
    }

    private void refreshMainFilters() {
        mainFilterWarehouses.clear();
        mainFilterWarehouses.addAll(warehouseStore.getWarehouses());

        mainWarehouseFilterAdapter.clear();
        mainWarehouseFilterAdapter.add(com.beaconfinder.app.data.AppLanguage.text("すべての倉庫"));
        for (Warehouse w : mainFilterWarehouses) {
            mainWarehouseFilterAdapter.add(w.name);
        }
        mainWarehouseFilterAdapter.notifyDataSetChanged();

        refreshMainAreaFilterSpinner();
    }

    private void refreshMainAreaFilterSpinner() {
        mainFilterAreas.clear();
        if (mainFilterWarehouseId.isEmpty()) {
            mainFilterAreas.addAll(warehouseStore.getAreas());
        } else {
            mainFilterAreas.addAll(warehouseStore.getAreasForWarehouse(mainFilterWarehouseId));
        }

        mainAreaFilterAdapter.clear();
        mainAreaFilterAdapter.add(com.beaconfinder.app.data.AppLanguage.text("すべてのエリア"));
        for (Area a : mainFilterAreas) {
            mainAreaFilterAdapter.add(a.name);
        }
        mainAreaFilterAdapter.notifyDataSetChanged();
        binding.mainFilterAreaSpinner.setSelection(0);
        mainFilterAreaId = "";
    }

    private void showCurrentBaseBeacons() {
        BaseStation base = currentBase();
        List<Beacon> allItems = base == null ? Collections.emptyList() : store.beaconsFor(base.sn);

        List<Beacon> filtered = new ArrayList<>();
        String q = (mainSearchQuery != null) ? mainSearchQuery.trim().toLowerCase(Locale.ROOT) : "";

        for (Beacon b : allItems) {
            Product p = warehouseStore.getProductByBeaconCode(b.code);

            // 倉庫フィルター
            if (!mainFilterWarehouseId.isEmpty()) {
                if (p == null || !mainFilterWarehouseId.equals(p.warehouseId)) {
                    continue;
                }
            }

            // エリアフィルター
            if (!mainFilterAreaId.isEmpty()) {
                if (p == null || !mainFilterAreaId.equals(p.areaId)) {
                    continue;
                }
            }

            // 検索キーワード
            if (!q.isEmpty()) {
                boolean matchCode = b.code.toLowerCase(Locale.ROOT).contains(q);
                boolean matchName = b.name.toLowerCase(Locale.ROOT).contains(q);
                boolean matchProd = false;
                if (p != null) {
                    boolean pName = p.name != null && p.name.toLowerCase(Locale.ROOT).contains(q);
                    boolean pSku = p.sku != null && p.sku.toLowerCase(Locale.ROOT).contains(q);
                    boolean pMemo = p.memo != null && p.memo.toLowerCase(Locale.ROOT).contains(q);
                    matchProd = pName || pSku || pMemo;
                }
                if (!matchCode && !matchName && !matchProd) {
                    continue;
                }
            }

            filtered.add(b);
        }

        beaconAdapter.submit(filtered);
        boolean empty = filtered.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.beaconList.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (base == null) {
            binding.emptyView.setText(com.beaconfinder.app.data.AppLanguage.text("先にロケーターを追加してください"));
        } else if (allItems.isEmpty()) {
            binding.emptyView.setText(com.beaconfinder.app.data.AppLanguage.text("登録されたビーコンがありません。QRコードをスキャンして追加してください"));
        } else {
            binding.emptyView.setText(com.beaconfinder.app.data.AppLanguage.text("検索条件「") + mainSearchQuery + com.beaconfinder.app.data.AppLanguage.text("」に一致する商品・ビーコンがありません"));
        }

        suppressSelectAllCallback = true;
        binding.selectAllCheckbox.setChecked(false);
        suppressSelectAllCallback = false;
        updateSelectionStatus();
        if (online && (base == null || !base.sn.equalsIgnoreCase(onlineSn))) {
            userDisconnected = true;
            gateway.disconnect();
            setOfflineUi(com.beaconfinder.app.data.AppLanguage.text("ロケーターが切り替えられました。再接続してください"));
        }
    }

    private void toggleConnection() {
        if (online) {
            userDisconnected = true;
            gateway.disconnect();
            setOfflineUi(com.beaconfinder.app.data.AppLanguage.text("切断済み"));
        } else {
            connectCurrentBase();
        }
    }

    private void connectCurrentBase() {
        BaseStation base = currentBase();
        if (base == null) {
            toast(com.beaconfinder.app.data.AppLanguage.text("先にロケーターを追加してください"));
            return;
        }

        acquireWifiLocks();
        if (connectTimeoutRunnable != null) {
            mainHandler.removeCallbacks(connectTimeoutRunnable);
        }

        gateway.disconnect();
        userDisconnected = false;
        online = false;
        onlineSn = "";
        binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● 接続試行中: ") + base.sn);
        binding.connectionStatus.setTextColor(getColor(R.color.warning));
        binding.connectButton.setText(com.beaconfinder.app.data.AppLanguage.text("キャンセル"));
        gateway.connect(base.sn);

        // 7秒のタイムアウト監視
        connectTimeoutRunnable = () -> {
            if (!online && !userDisconnected) {
                binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● 未接続（応答なし）"));
                binding.connectionStatus.setTextColor(getColor(R.color.offline));
                binding.connectButton.setText(com.beaconfinder.app.data.AppLanguage.text("再試行"));
                binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ロケーターからの応答がありません。同一Wi-Fiルーター（LAN）に接続されているか確認してください"));
            }
        };
        mainHandler.postDelayed(connectTimeoutRunnable, 7000);
    }

    private void confirmDeleteBase() {
        BaseStation base = currentBase();
        if (base == null) return;
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("ロケーターを削除しますか？"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("ロケーター「") + base.name + com.beaconfinder.app.data.AppLanguage.text("」および紐づくビーコン情報がリストから削除されます。"))
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("削除"), (dialog, which) -> {
                    try {
                        if (base.sn.equalsIgnoreCase(onlineSn)) {
                            userDisconnected = true;
                            gateway.disconnect();
                        }
                        // suppressコールバックを立てて、スピナー選択をリセットしてからリスト変更
                        suppressSelectAllCallback = true;
                        binding.baseSpinner.setSelection(android.widget.AdapterView.INVALID_POSITION);
                        store.removeBase(base.sn);
                        baseAdapter.notifyDataSetChanged();
                        suppressSelectAllCallback = false;
                        setOfflineUi(com.beaconfinder.app.data.AppLanguage.text("未接続"));
                        // 残りのロケーターがあれば先頭を選択
                        if (!store.bases().isEmpty()) {
                            binding.baseSpinner.setSelection(0);
                        }
                        showCurrentBaseBeacons();
                    } catch (Exception ex) {
                        suppressSelectAllCallback = false;
                        android.util.Log.e("MainActivity", "Delete base error", ex);
                        Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("削除中にエラーが発生しました: ") + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void controlSelected(boolean stop) {
        List<Beacon> selected = beaconAdapter.selected();
        if (selected.isEmpty()) {
            toast(com.beaconfinder.app.data.AppLanguage.text("少なくとも1つのビーコンを選択してください"));
            return;
        }
        control(selected, stop);
    }

    private void controlBeaconWithConfig(Beacon beacon) {
        if (SharedSync.get(this).configured()) {
            control(Collections.singletonList(beacon), false);
            return;
        }
        AppConfig config = AppConfig.getInstance(this);
        BluetoothStationManager bleMgr = BluetoothStationManager.getInstance(this);
        List<String> codes = Collections.singletonList(beacon.code);

        // メイン画面の現在の選択値（色・点滅・音声）を最優先で使用
        int color = selectedColor();
        boolean flash = binding.flashSwitch.isChecked();
        boolean beep = binding.beepSwitch.isChecked();
        int workTimeSec = config.lightDurationSec;
        float flashInterval = config.flashIntervalSec;

        try {
            if (bleMgr.isConnected()) {
                String ret = bleMgr.light(codes, color, flash, beep, workTimeSec, flashInterval);
                if ("0".equals(ret)) {
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + beacon.code + com.beaconfinder.app.data.AppLanguage.text(" 接続・点灯成功！"));
                    binding.operationStatus.setTextColor(getColor(R.color.online));
                    toast(com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + beacon.code + com.beaconfinder.app.data.AppLanguage.text(" 点灯成功！"));
                } else {
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("点灯失敗 (SDK応答: ") + ret + ")");
                    binding.operationStatus.setTextColor(getColor(R.color.offline));
                }
            } else if (online) {
                String ret = gateway.light(codes, color, flash, beep);
                if ("0".equals(ret)) {
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + beacon.code + com.beaconfinder.app.data.AppLanguage.text(" 接続・点灯成功（LAN）！"));
                    binding.operationStatus.setTextColor(getColor(R.color.online));
                    toast(com.beaconfinder.app.data.AppLanguage.text("ビーコン ") + beacon.code + com.beaconfinder.app.data.AppLanguage.text(" 点灯成功！"));
                } else {
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("LAN点灯失敗 (SDK: ") + ret + ")");
                    binding.operationStatus.setTextColor(getColor(R.color.offline));
                }
            } else {
                toast(com.beaconfinder.app.data.AppLanguage.text("ロケーター未接続。自動接続を試行中…"));
                autoConnectBluetoothBase();
            }
        } catch (Exception e) {
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("点灯制御エラー: ") + e.getMessage());
            binding.operationStatus.setTextColor(getColor(R.color.offline));
        }
    }

    private void control(List<Beacon> beacons, boolean stop) {
        if (SharedSync.get(this).configured()) {
            WarehouseBridgeService.start(this);
            SharedSync.get(this).find(new ArrayList<>(beacons), stop, selectedColor(), binding.flashSwitch.isChecked(), binding.beepSwitch.isChecked());
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("已提交联动任务，地图中可查看位置与执行状态"));
            return;
        }
        BluetoothStationManager bleMgr = BluetoothStationManager.getInstance(this);
        boolean isBleConnected = bleMgr.isConnected();

        if (!online && !isBleConnected) {
            toast(com.beaconfinder.app.data.AppLanguage.text("ロケーター（LANまたはBluetooth）に接続してください"));
            return;
        }

        List<String> codes = new ArrayList<>();
        for (Beacon beacon : beacons) codes.add(beacon.code);

        // メイン画面の選択値を反映
        int color = selectedColor();
        boolean flash = binding.flashSwitch.isChecked();
        boolean beep = binding.beepSwitch.isChecked();

        // Bluetoothロケーターが接続されており、LAN未接続またはBluetoothロケーターが選択されている場合
        BaseStation base = currentBase();
        boolean isBleBaseSelected = base != null && (base.sn.toLowerCase(Locale.ROOT).contains("ble") || base.sn.toLowerCase(Locale.ROOT).contains("flashfind") || base.name.toLowerCase(Locale.ROOT).contains("bluetooth") || base.name.contains("Sanray"));

        if (isBleConnected && (!online || isBleBaseSelected)) {
            AppConfig config = AppConfig.getInstance(this);
            try {
                String result = stop
                        ? bleMgr.stop(codes)
                        : bleMgr.light(codes, color, flash, beep, config.lightDurationSec, config.flashIntervalSec);
                if ("0".equals(result)) {
                    binding.operationStatus.setText((stop ? com.beaconfinder.app.data.AppLanguage.text("消灯") : com.beaconfinder.app.data.AppLanguage.text("点灯")) + com.beaconfinder.app.data.AppLanguage.text("コマンド送信完了（Bluetooth経由 / 対象: ") + codes.size() + com.beaconfinder.app.data.AppLanguage.text("件）"));
                    binding.operationStatus.setTextColor(getColor(R.color.online));
                } else {
                    binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("Bluetoothコマンド送信失敗 (コード: ") + result + ")");
                    binding.operationStatus.setTextColor(getColor(R.color.offline));
                }
            } catch (Exception error) {
                binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("Bluetoothコマンド送信エラー: ") + error.getMessage());
                binding.operationStatus.setTextColor(getColor(R.color.offline));
            }
            return;
        }

        // LAN ロケーター経由
        if (!requireOnline()) return;
        try {
            String result = stop
                    ? gateway.stop(codes)
                    : gateway.light(codes, color, flash, beep);
            if ("0".equals(result)) {
                binding.operationStatus.setText((stop ? com.beaconfinder.app.data.AppLanguage.text("消灯") : com.beaconfinder.app.data.AppLanguage.text("点灯")) + com.beaconfinder.app.data.AppLanguage.text("コマンド送信完了（LAN経由 / 対象: ") + codes.size() + com.beaconfinder.app.data.AppLanguage.text("件）"));
                binding.operationStatus.setTextColor(getColor(R.color.online));
            } else {
                binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("コマンド送信失敗 (SDKコード: ") + result + ")");
                binding.operationStatus.setTextColor(getColor(R.color.offline));
            }
        } catch (Exception error) {
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("コマンド送信エラー: ") + error.getMessage());
            binding.operationStatus.setTextColor(getColor(R.color.offline));
        }
    }

    private int colorCodeToIndex(int colorCode) {
        switch (colorCode) {
            case AppConfig.COLOR_RED: return 0;
            case AppConfig.COLOR_YELLOW: return 1;
            case AppConfig.COLOR_BLUE: return 2;
            case AppConfig.COLOR_GREEN: return 3;
            case AppConfig.COLOR_CYAN: return 4;
            case AppConfig.COLOR_WHITE: return 5;
            case AppConfig.COLOR_PURPLE: return 6;
            default: return 3;
        }
    }

    private int indexToColorCode(int index) {
        int[] values = {
            AppConfig.COLOR_RED,
            AppConfig.COLOR_YELLOW,
            AppConfig.COLOR_BLUE,
            AppConfig.COLOR_GREEN,
            AppConfig.COLOR_CYAN,
            AppConfig.COLOR_WHITE,
            AppConfig.COLOR_PURPLE
        };
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private int selectedColor() {
        return indexToColorCode(binding.colorSpinner.getSelectedItemPosition());
    }

    private boolean requireOnline() {
        BaseStation base = currentBase();
        if (!online || base == null || !base.sn.equalsIgnoreCase(onlineSn)) {
            toast(com.beaconfinder.app.data.AppLanguage.text("現在のロケーターに接続してください"));
            return false;
        }
        return true;
    }

    private void setOfflineUi(String status) {
        online = false;
        onlineSn = "";
        binding.connectionStatus.setText("● " + status);
        binding.connectionStatus.setTextColor(getColor(R.color.offline));
        binding.connectButton.setText(com.beaconfinder.app.data.AppLanguage.text("接続"));
    }

    private void updateSelectionStatus() {
        int count = beaconAdapter.selected().size();
        binding.lightSelectedButton.setText(count == 0 ? com.beaconfinder.app.data.AppLanguage.text("選択分を点灯") : com.beaconfinder.app.data.AppLanguage.text("選択分を点灯 (") + count + ")");
        suppressSelectAllCallback = true;
        binding.selectAllCheckbox.setChecked(beaconAdapter.allSelected());
        suppressSelectAllCallback = false;
    }

    @Override public void onLight(Beacon beacon) {
        control(Collections.singletonList(beacon), false);
    }

    @Override public void onDelete(Beacon beacon) {
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("ビーコンを削除しますか？"))
                .setMessage(beacon.name + "\nID: " + beacon.code)
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("削除"), (dialog, which) -> {
                    store.removeBeacon(beacon);
                    showCurrentBaseBeacons();
                }).show();
    }

    @Override public void onSelectionChanged() { updateSelectionStatus(); }

    @Override public void onConnectionChanged(String sn, boolean isOnline) {
        runOnUiThread(() -> {
            if (connectTimeoutRunnable != null) {
                mainHandler.removeCallbacks(connectTimeoutRunnable);
            }
            BaseStation base = currentBase();
            if (isOnline) {
                userDisconnected = false;
                online = true;
                onlineSn = sn;

                // もし登録時の SN と実際のロケーター SN で大文字小文字等の表記揺れがあった場合、正規化して保存
                if (base != null && !base.sn.equals(sn) && (base.sn.equalsIgnoreCase(sn) || base.sn.equals(sn))) {
                    store.addBase(new BaseStation(sn, base.name));
                    refreshBasesAndSelect(sn);
                }

                binding.connectionStatus.setText(com.beaconfinder.app.data.AppLanguage.text("● 接続完了 · ") + sn);
                binding.connectionStatus.setTextColor(getColor(R.color.online));
                binding.connectButton.setText(com.beaconfinder.app.data.AppLanguage.text("切断"));
                binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ロケーターに正常に接続しました"));
            } else if (base == null || sn == null || sn.equalsIgnoreCase(base.sn) || sn.equalsIgnoreCase(onlineSn)) {
                setOfflineUi(userDisconnected ? com.beaconfinder.app.data.AppLanguage.text("切断済み") : com.beaconfinder.app.data.AppLanguage.text("オフライン · 自動再接続中"));
            }
        });
    }

    @Override public void onCommandResult(boolean success, String message) {
        runOnUiThread(() -> {
            binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text(message));
            binding.operationStatus.setTextColor(getColor(success ? R.color.online : R.color.offline));
        });
    }

    @Override public void onBeaconReport(String code, String state) {
        runOnUiThread(() -> {
            if (onlineSn.isEmpty()) return;
            Beacon beacon = store.findBeacon(onlineSn, code);
            if (beacon != null) {
                beacon.state = state;
                beaconAdapter.refresh(beacon);
            }
        });
    }

    @Override public void onDiscoveredBase(String id, String ip, String port, String mac, boolean appControlStatus) {
        discoveredBases.put(id, new DiscoveredBase(id, ip, port, mac, appControlStatus));
        runOnUiThread(() -> {
            BaseStation base = currentBase();
            if (base != null && !online && !userDisconnected) {
                if (id.equalsIgnoreCase(base.sn) || (base.sn != null && base.sn.equals(ip))) {
                    if (appControlStatus) {
                        binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + id + " (" + ip + com.beaconfinder.app.data.AppLanguage.text(") を検出しました。TCP接続を確立中…"));
                    } else {
                        binding.operationStatus.setText(com.beaconfinder.app.data.AppLanguage.text("ロケーター ") + id + com.beaconfinder.app.data.AppLanguage.text(" を検出しましたが、他のクライアントが接続中です"));
                    }
                }
            }
        });
    }

    @Override public void onRawMessage(String message) {
        // 開発ログ用
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        if (connectTimeoutRunnable != null) {
            mainHandler.removeCallbacks(connectTimeoutRunnable);
        }
        gateway.setListener(null);
        userDisconnected = true;
        if (!SharedSync.get(this).configured()) gateway.disconnect();
        releaseWifiLocks();
        super.onDestroy();
    }
}
