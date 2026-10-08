package com.beaconfinder.app.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.beaconfinder.app.R;
import com.beaconfinder.app.data.DeviceStore;
import com.beaconfinder.app.databinding.ActivityBluetoothConfigBinding;
import com.beaconfinder.app.model.BaseStation;
import com.beaconfinder.app.sdk.BluetoothStationManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import feasyblue.BleStateTypes;

public class BluetoothConfigActivity extends AppCompatActivity implements BluetoothStationManager.Listener {

    private static final int REQ_BLE_PERMISSIONS = 2001;

    private ActivityBluetoothConfigBinding binding;
    private BluetoothStationManager bleManager;
    private BluetoothDeviceAdapter deviceAdapter;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private final int[] rssiValues = {-90, -80, -70, -60, -50};

    @Override protected void onResume() {
        super.onResume();
        if (!getResources().getConfiguration().getLocales().get(0).getLanguage().equals(com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage())) {
            recreate(); return;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = com.beaconfinder.app.data.AppLanguage.locale(newBase);
        Locale.setDefault(locale);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityBluetoothConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ステータスバーとの重なりを防止
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, statusBars.top, 0, 0);
            return insets;
        });

        // ツールバー
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        bleManager = BluetoothStationManager.getInstance(this);
        bleManager.setListener(this);

        // RSSI スピナー
        ArrayAdapter<String> rssiAdapter = new ArrayAdapter<>(this,
                R.layout.item_spinner_selected,
                new String[]{
                        com.beaconfinder.app.data.AppLanguage.text("-90 dBm (最遠・全受信)"),
                        com.beaconfinder.app.data.AppLanguage.text("-80 dBm (広範囲)"),
                        com.beaconfinder.app.data.AppLanguage.text("-70 dBm (標準)"),
                        com.beaconfinder.app.data.AppLanguage.text("-60 dBm (近距離)"),
                        com.beaconfinder.app.data.AppLanguage.text("-50 dBm (至近距離のみ)")
                });
        rssiAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spRssiFilter.setAdapter(rssiAdapter);
        binding.spRssiFilter.setSelection(0);

        // デバイスリスト RecyclerView
        deviceAdapter = new BluetoothDeviceAdapter(item -> {
            binding.etBleName.setText(item.name);
            log(com.beaconfinder.app.data.AppLanguage.text("デバイスを選択しました: ") + item.name + " (" + item.address + ")");
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("選択: ") + item.name, Toast.LENGTH_SHORT).show();
        });
        binding.rvBleDevices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBleDevices.setAdapter(deviceAdapter);

        // ログ TextView スクロール有効化
        binding.tvBleLog.setMovementMethod(new ScrollingMovementMethod());

        // ボタンリスナー
        binding.btnBleScan.setOnClickListener(v -> toggleScan());
        binding.btnBleConnect.setOnClickListener(v -> toggleConnect());
        binding.btnClearDevices.setOnClickListener(v -> {
            deviceAdapter.clear();
            updateDeviceListVisibility();
        });
        binding.btnClearLog.setOnClickListener(v -> binding.tvBleLog.setText(""));

        binding.btnTestLight.setOnClickListener(v -> testLight());
        binding.btnTestStop.setOnClickListener(v -> testStop());
        binding.btnGetVersion.setOnClickListener(v -> showVersionInfo());
        binding.btnSetHeart.setOnClickListener(v -> showHeartSettingDialog());

        // 初期の状態反映
        updateConnectionUi();
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBlePermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        ActivityCompat.requestPermissions(this, list.toArray(new String[0]), REQ_BLE_PERMISSIONS);
    }

    private void toggleScan() {
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("Bluetoothを有効にしてください"), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        if (bleManager.isScanning()) {
            bleManager.stopScan();
            binding.btnBleScan.setText(com.beaconfinder.app.data.AppLanguage.text("周辺スキャン"));
        } else {
            deviceAdapter.clear();
            updateDeviceListVisibility();
            bleManager.startScan();
            binding.btnBleScan.setText(com.beaconfinder.app.data.AppLanguage.text("スキャン停止"));
        }
    }

    private void toggleConnect() {
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }

        if (bleManager.isConnected()) {
            bleManager.disconnect();
            updateConnectionUi();
        } else {
            String name = binding.etBleName.getText().toString().trim();
            if (name.isEmpty()) name = BluetoothStationManager.DEFAULT_BLE_NAME;

            int index = binding.spRssiFilter.getSelectedItemPosition();
            int filterRssi = (index >= 0 && index < rssiValues.length) ? rssiValues[index] : -90;

            bleManager.connect(name, true, filterRssi);
            binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● 接続試行中…"));
            binding.tvBleStatusBadge.setTextColor(getColor(R.color.warning));
            binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("切断"));
        }
    }

    private void updateConnectionUi() {
        boolean connected = bleManager.isConnected();
        if (connected) {
            binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● 接続完了"));
            binding.tvBleStatusBadge.setTextColor(getColor(R.color.online));
            binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("切断"));
        } else {
            binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● 未接続"));
            binding.tvBleStatusBadge.setTextColor(getColor(R.color.offline));
            binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("接続開始"));
        }
    }

    private void testLight() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("先にBluetoothロケーターに接続してください"), Toast.LENGTH_SHORT).show();
            return;
        }
        String ret = bleManager.testLightAll();
        if ("0".equals(ret)) {
            log(com.beaconfinder.app.data.AppLanguage.text("テスト点灯コマンドを送信しました"));
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("テスト点灯送信完了"), Toast.LENGTH_SHORT).show();
        } else {
            log(com.beaconfinder.app.data.AppLanguage.text("テスト点灯失敗 (コード: ") + ret + ")");
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("送信失敗: ") + ret, Toast.LENGTH_SHORT).show();
        }
    }

    private void testStop() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("先にBluetoothロケーターに接続してください"), Toast.LENGTH_SHORT).show();
            return;
        }
        String ret = bleManager.testStopAll();
        if ("0".equals(ret)) {
            log(com.beaconfinder.app.data.AppLanguage.text("全消灯コマンドを送信しました"));
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("全消灯送信完了"), Toast.LENGTH_SHORT).show();
        } else {
            log(com.beaconfinder.app.data.AppLanguage.text("全消灯失敗 (コード: ") + ret + ")");
        }
    }

    private void showVersionInfo() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("先にBluetoothロケーターに接続してください"), Toast.LENGTH_SHORT).show();
            return;
        }
        String firm = bleManager.getFirmwareVersion();
        String hard = bleManager.getHardwareVersion();
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("Bluetoothロケーターバージョン情報"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("ファームウェア: ") + firm + com.beaconfinder.app.data.AppLanguage.text("\nハードウェア: ") + hard)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showHeartSettingDialog() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("先にBluetoothロケーターに接続してください"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {com.beaconfinder.app.data.AppLanguage.text("10 秒 (推奨)"), com.beaconfinder.app.data.AppLanguage.text("20 秒"), com.beaconfinder.app.data.AppLanguage.text("30 秒"), com.beaconfinder.app.data.AppLanguage.text("60 秒")};
        int[] seconds = {10, 20, 30, 60};
        new AlertDialog.Builder(this)
                .setTitle(com.beaconfinder.app.data.AppLanguage.text("ビーコン待受（ハートビート）間隔設定"))
                .setItems(options, (d, which) -> {
                    int sec = seconds[which];
                    String ret = bleManager.setTagHeart(sec);
                    if ("0".equals(ret)) {
                        log(com.beaconfinder.app.data.AppLanguage.text("ビーコンハートビートを ") + sec + com.beaconfinder.app.data.AppLanguage.text(" 秒に設定しました"));
                        Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("設定完了 (") + sec + com.beaconfinder.app.data.AppLanguage.text("秒)"), Toast.LENGTH_SHORT).show();
                    } else {
                        log(com.beaconfinder.app.data.AppLanguage.text("設定失敗 (コード: ") + ret + ")");
                    }
                })
                .setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("キャンセル"), null)
                .show();
    }

    private void updateDeviceListVisibility() {
        if (deviceAdapter.getCount() > 0) {
            binding.tvDevicesEmpty.setVisibility(View.GONE);
            binding.rvBleDevices.setVisibility(View.VISIBLE);
        } else {
            binding.tvDevicesEmpty.setVisibility(View.VISIBLE);
            binding.rvBleDevices.setVisibility(View.GONE);
        }
    }

    private void log(String msg) {
        String line = "[" + timeFormat.format(new Date()) + "] " + msg + "\n";
        binding.tvBleLog.append(line);
    }

    @Override
    public void onBleStateChanged(int state, int rssi, String stateDesc) {
        runOnUiThread(() -> {
            switch (state) {
                case BleStateTypes.Connected:
                    binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● 接続完了 (") + rssi + " dBm)");
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.online));
                    binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("切断"));
                    try {
                        String bleName = binding.etBleName.getText().toString().trim();
                        if (bleName.isEmpty()) bleName = BluetoothStationManager.DEFAULT_BLE_NAME;
                        DeviceStore store = DeviceStore.getInstance(this);
                        store.addBase(new BaseStation(bleName, com.beaconfinder.app.data.AppLanguage.text("Bluetoothロケーター (") + bleName + ")"));
                    } catch (Exception ignored) {}
                    break;
                case BleStateTypes.Connecting:
                    binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● 接続試行中…"));
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.warning));
                    binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("キャンセル"));
                    break;
                case BleStateTypes.Scaning:
                    binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● ロケーターを探索中…"));
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.warning));
                    binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("キャンセル"));
                    break;
                case BleStateTypes.DisConnected:
                default:
                    binding.tvBleStatusBadge.setText(com.beaconfinder.app.data.AppLanguage.text("● 未接続"));
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.offline));
                    binding.btnBleConnect.setText(com.beaconfinder.app.data.AppLanguage.text("接続開始"));
                    break;
            }
        });
    }

    @Override
    public void onDeviceFound(String name, String address, int rssi) {
        runOnUiThread(() -> {
            deviceAdapter.addOrUpdate(name, address, rssi);
            updateDeviceListVisibility();
        });
    }

    @Override
    public void onMessageReceived(String data) {
        runOnUiThread(() -> log(com.beaconfinder.app.data.AppLanguage.text("受信: ") + data));
    }

    @Override
    public void onLog(String message) {
        runOnUiThread(() -> log(message));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("Bluetooth権限を取得しました"), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("Bluetooth利用には権限の許可が必要です"), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        bleManager.stopScan();
        bleManager.setListener(null);
        super.onDestroy();
    }
}
