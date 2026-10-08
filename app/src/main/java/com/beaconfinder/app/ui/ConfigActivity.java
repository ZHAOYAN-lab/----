package com.beaconfinder.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.beaconfinder.app.R;
import com.beaconfinder.app.data.AppConfig;
import com.beaconfinder.app.databinding.ActivityConfigReplicaBinding;
import com.beaconfinder.app.sdk.BluetoothStationManager;

import java.util.Locale;

import feasyblue.BleStateTypes;

public class ConfigActivity extends AppCompatActivity implements BluetoothStationManager.Listener {

    private ActivityConfigReplicaBinding binding;
    private AppConfig config;
    private BluetoothStationManager bleManager;

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
        binding = ActivityConfigReplicaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        config = AppConfig.getInstance(this);
        bleManager = BluetoothStationManager.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        loadUiFromConfig();

        // BLEロケーター & MINIロケーター スイッチ連動
        binding.swBluetoothBase.setOnCheckedChangeListener((btn, isChecked) -> {
            config.isBluetoothBase = isChecked;
            onBaseSwitchChanged();
        });
        binding.swMiniBase.setOnCheckedChangeListener((btn, isChecked) -> {
            config.isMiniBase = isChecked;
            onBaseSwitchChanged();
        });

        // BLEデバイス探索ボタン → BluetoothConfigActivity を開く
        binding.btnBleScanConnect.setOnClickListener(v -> {
            startActivity(new Intent(this, BluetoothConfigActivity.class));
        });

        // デフォルトバインド情報 カプセル切り替え
        binding.layoutDefaultBind.setOnClickListener(v -> {
            config.defaultBindInfo = !config.defaultBindInfo;
            updateDefaultBindPill();
        });

        // 固定カラー カプセル切り替え
        binding.layoutFixedColor.setOnClickListener(v -> {
            config.fixedColor = !config.fixedColor;
            updateFixedColorPill();
        });

        // カラー単一選択連動
        binding.rgColors.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.rbRed.getId()) config.selectedColor = AppConfig.COLOR_RED;
            else if (checkedId == binding.rbYellow.getId()) config.selectedColor = AppConfig.COLOR_YELLOW;
            else if (checkedId == binding.rbBlue.getId()) config.selectedColor = AppConfig.COLOR_BLUE;
            else if (checkedId == binding.rbGreen.getId()) config.selectedColor = AppConfig.COLOR_GREEN;
            else if (checkedId == binding.rbCyan.getId()) config.selectedColor = AppConfig.COLOR_CYAN;
            else if (checkedId == binding.rbWhite.getId()) config.selectedColor = AppConfig.COLOR_WHITE;
            else if (checkedId == binding.rbPurple.getId()) config.selectedColor = AppConfig.COLOR_PURPLE;
        });

        // 設定保存ボタン
        binding.btnSaveConfig.setOnClickListener(v -> saveConfig());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getResources().getConfiguration().getLocales().get(0).getLanguage().equals(com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage())) {
            recreate(); return;
        }

        bleManager.setListener(this);
        updateBleStatusUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        bleManager.setListener(null);
    }

    private void updateBleStatusUi() {
        boolean connected = bleManager.isConnected();
        if (connected) {
            binding.tvBleStatusConfig.setText(com.beaconfinder.app.data.AppLanguage.text("● BLE: 接続中"));
            binding.tvBleStatusConfig.setTextColor(getColor(R.color.online));
            binding.btnBleScanConnect.setText(com.beaconfinder.app.data.AppLanguage.text("BLE設定を開く"));
        } else {
            binding.tvBleStatusConfig.setText(com.beaconfinder.app.data.AppLanguage.text("● BLE: 未接続 — タップしてデバイスを探す"));
            binding.tvBleStatusConfig.setTextColor(getColor(R.color.offline));
            binding.btnBleScanConnect.setText(com.beaconfinder.app.data.AppLanguage.text("BLEデバイスを探す"));
        }
    }

    private void loadUiFromConfig() {
        binding.swBluetoothBase.setChecked(config.isBluetoothBase);
        binding.swMiniBase.setChecked(config.isMiniBase);
        updateDefaultBindPill();

        binding.swFastBind.setChecked(config.fastBind);
        binding.etBindDelay.setText(String.valueOf(config.bindDelayMs));

        binding.swSoundPrompt.setChecked(config.soundPrompt);
        binding.etLightDuration.setText(String.valueOf(config.lightDurationSec));

        binding.swFlashPrompt.setChecked(config.flashPrompt);
        binding.etFlashInterval.setText(String.valueOf(config.flashIntervalSec));

        binding.swMultiColorCycle.setChecked(config.multiColorCycle);
        updateFixedColorPill();

        switch (config.selectedColor) {
            case AppConfig.COLOR_RED: binding.rbRed.setChecked(true); break;
            case AppConfig.COLOR_YELLOW: binding.rbYellow.setChecked(true); break;
            case AppConfig.COLOR_BLUE: binding.rbBlue.setChecked(true); break;
            case AppConfig.COLOR_GREEN: default: binding.rbGreen.setChecked(true); break;
            case AppConfig.COLOR_CYAN: binding.rbCyan.setChecked(true); break;
            case AppConfig.COLOR_WHITE: binding.rbWhite.setChecked(true); break;
            case AppConfig.COLOR_PURPLE: binding.rbPurple.setChecked(true); break;
        }

        binding.etInventoryDuration.setText(String.valueOf(config.inventoryDurationSec));
        binding.etVoltageWarning.setText(String.valueOf(config.voltageWarningMv));
        binding.etSignalFilter.setText(String.valueOf(config.signalFilterRssi));
    }

    private void updateDefaultBindPill() {
        binding.viewDefaultBindThumb.setVisibility(config.defaultBindInfo ? View.VISIBLE : View.INVISIBLE);
        binding.layoutDefaultBind.setAlpha(config.defaultBindInfo ? 1.0f : 0.6f);
    }

    private void updateFixedColorPill() {
        binding.viewFixedColorThumb.setVisibility(config.fixedColor ? View.VISIBLE : View.INVISIBLE);
        binding.layoutFixedColor.setAlpha(config.fixedColor ? 1.0f : 0.6f);
    }

    private void onBaseSwitchChanged() {
        if (config.isBluetoothBase || config.isMiniBase) {
            if (!bleManager.isConnected()) {
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("BLEデバイスを探しています..."), Toast.LENGTH_SHORT).show();
                int filter = config.signalFilterRssi > 0 ? -config.signalFilterRssi : -90;
                bleManager.autoConnect(filter);
            }
        } else {
            if (bleManager.isConnected()) {
                bleManager.disconnect();
                Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("BLEロケーターを切断しました"), Toast.LENGTH_SHORT).show();
            }
        }
        updateBleStatusUi();
    }

    private void saveConfig() {
        try {
            config.isBluetoothBase = binding.swBluetoothBase.isChecked();
            config.isMiniBase = binding.swMiniBase.isChecked();

            config.fastBind = binding.swFastBind.isChecked();
            config.bindDelayMs = Integer.parseInt(binding.etBindDelay.getText().toString().trim());

            config.soundPrompt = binding.swSoundPrompt.isChecked();
            config.lightDurationSec = Integer.parseInt(binding.etLightDuration.getText().toString().trim());

            config.flashPrompt = binding.swFlashPrompt.isChecked();
            config.flashIntervalSec = Float.parseFloat(binding.etFlashInterval.getText().toString().trim());

            config.multiColorCycle = binding.swMultiColorCycle.isChecked();

            config.inventoryDurationSec = Integer.parseInt(binding.etInventoryDuration.getText().toString().trim());
            config.voltageWarningMv = Integer.parseInt(binding.etVoltageWarning.getText().toString().trim());
            config.signalFilterRssi = Integer.parseInt(binding.etSignalFilter.getText().toString().trim());

            config.save(this);

            int filter = config.signalFilterRssi > 0 ? -config.signalFilterRssi : -90;
            bleManager.setFilterRssi(filter);

            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("設定を保存しました"), Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, com.beaconfinder.app.data.AppLanguage.text("入力値に誤りがあります。各項目を確認してください"), Toast.LENGTH_SHORT).show();
        }
    }

    // ── BluetoothStationManager.Listener ──────────────────────────────────

    @Override
    public void onBleStateChanged(int state, int rssi, String stateDesc) {
        runOnUiThread(this::updateBleStatusUi);
    }

    @Override
    public void onDeviceFound(String name, String address, int rssi) { /* not used here */ }

    @Override
    public void onMessageReceived(String data) { /* not used here */ }

    @Override
    public void onLog(String message) { /* not used here */ }
}

