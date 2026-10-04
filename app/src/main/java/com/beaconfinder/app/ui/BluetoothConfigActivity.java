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

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = Locale.JAPAN;
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
                        "-90 dBm (最遠・全受信)",
                        "-80 dBm (広範囲)",
                        "-70 dBm (標準)",
                        "-60 dBm (近距離)",
                        "-50 dBm (至近距離のみ)"
                });
        rssiAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spRssiFilter.setAdapter(rssiAdapter);
        binding.spRssiFilter.setSelection(0);

        // デバイスリスト RecyclerView
        deviceAdapter = new BluetoothDeviceAdapter(item -> {
            binding.etBleName.setText(item.name);
            log("デバイスを選択しました: " + item.name + " (" + item.address + ")");
            Toast.makeText(this, "選択: " + item.name, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Bluetoothを有効にしてください", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        if (bleManager.isScanning()) {
            bleManager.stopScan();
            binding.btnBleScan.setText("周辺スキャン");
        } else {
            deviceAdapter.clear();
            updateDeviceListVisibility();
            bleManager.startScan();
            binding.btnBleScan.setText("スキャン停止");
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
            binding.tvBleStatusBadge.setText("● 接続試行中…");
            binding.tvBleStatusBadge.setTextColor(getColor(R.color.warning));
            binding.btnBleConnect.setText("切断");
        }
    }

    private void updateConnectionUi() {
        boolean connected = bleManager.isConnected();
        if (connected) {
            binding.tvBleStatusBadge.setText("● 接続完了");
            binding.tvBleStatusBadge.setTextColor(getColor(R.color.online));
            binding.btnBleConnect.setText("切断");
        } else {
            binding.tvBleStatusBadge.setText("● 未接続");
            binding.tvBleStatusBadge.setTextColor(getColor(R.color.offline));
            binding.btnBleConnect.setText("接続開始");
        }
    }

    private void testLight() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, "先にBluetoothロケーターに接続してください", Toast.LENGTH_SHORT).show();
            return;
        }
        String ret = bleManager.testLightAll();
        if ("0".equals(ret)) {
            log("テスト点灯コマンドを送信しました");
            Toast.makeText(this, "テスト点灯送信完了", Toast.LENGTH_SHORT).show();
        } else {
            log("テスト点灯失敗 (コード: " + ret + ")");
            Toast.makeText(this, "送信失敗: " + ret, Toast.LENGTH_SHORT).show();
        }
    }

    private void testStop() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, "先にBluetoothロケーターに接続してください", Toast.LENGTH_SHORT).show();
            return;
        }
        String ret = bleManager.testStopAll();
        if ("0".equals(ret)) {
            log("全消灯コマンドを送信しました");
            Toast.makeText(this, "全消灯送信完了", Toast.LENGTH_SHORT).show();
        } else {
            log("全消灯失敗 (コード: " + ret + ")");
        }
    }

    private void showVersionInfo() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, "先にBluetoothロケーターに接続してください", Toast.LENGTH_SHORT).show();
            return;
        }
        String firm = bleManager.getFirmwareVersion();
        String hard = bleManager.getHardwareVersion();
        new AlertDialog.Builder(this)
                .setTitle("Bluetoothロケーターバージョン情報")
                .setMessage("ファームウェア: " + firm + "\nハードウェア: " + hard)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showHeartSettingDialog() {
        if (!bleManager.isConnected()) {
            Toast.makeText(this, "先にBluetoothロケーターに接続してください", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {"10 秒 (推奨)", "20 秒", "30 秒", "60 秒"};
        int[] seconds = {10, 20, 30, 60};
        new AlertDialog.Builder(this)
                .setTitle("ビーコン待受（ハートビート）間隔設定")
                .setItems(options, (d, which) -> {
                    int sec = seconds[which];
                    String ret = bleManager.setTagHeart(sec);
                    if ("0".equals(ret)) {
                        log("ビーコンハートビートを " + sec + " 秒に設定しました");
                        Toast.makeText(this, "設定完了 (" + sec + "秒)", Toast.LENGTH_SHORT).show();
                    } else {
                        log("設定失敗 (コード: " + ret + ")");
                    }
                })
                .setNegativeButton("キャンセル", null)
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
                    binding.tvBleStatusBadge.setText("● 接続完了 (" + rssi + " dBm)");
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.online));
                    binding.btnBleConnect.setText("切断");
                    try {
                        String bleName = binding.etBleName.getText().toString().trim();
                        if (bleName.isEmpty()) bleName = BluetoothStationManager.DEFAULT_BLE_NAME;
                        DeviceStore store = new DeviceStore(this);
                        store.addBase(new BaseStation(bleName, "Bluetoothロケーター (" + bleName + ")"));
                    } catch (Exception ignored) {}
                    break;
                case BleStateTypes.Connecting:
                    binding.tvBleStatusBadge.setText("● 接続試行中…");
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.warning));
                    binding.btnBleConnect.setText("キャンセル");
                    break;
                case BleStateTypes.Scaning:
                    binding.tvBleStatusBadge.setText("● ロケーターを探索中…");
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.warning));
                    binding.btnBleConnect.setText("キャンセル");
                    break;
                case BleStateTypes.DisConnected:
                default:
                    binding.tvBleStatusBadge.setText("● 未接続");
                    binding.tvBleStatusBadge.setTextColor(getColor(R.color.offline));
                    binding.btnBleConnect.setText("接続開始");
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
        runOnUiThread(() -> log("受信: " + data));
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
                Toast.makeText(this, "Bluetooth権限を取得しました", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth利用には権限の許可が必要です", Toast.LENGTH_LONG).show();
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
