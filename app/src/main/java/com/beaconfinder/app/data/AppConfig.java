package com.beaconfinder.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {

    private static final String PREF_NAME = "app_config_prefs";

    public static final int COLOR_RED = 0x04;
    public static final int COLOR_YELLOW = 0x06;
    public static final int COLOR_BLUE = 0x01;
    public static final int COLOR_GREEN = 0x02;
    public static final int COLOR_CYAN = 0x03;
    public static final int COLOR_WHITE = 0x07;
    public static final int COLOR_PURPLE = 0x05;

    public boolean isBluetoothBase = true;
    public boolean isMiniBase = true;
    public boolean defaultBindInfo = true;
    public boolean fastBind = true;
    public int bindDelayMs = 200;
    public boolean soundPrompt = true;
    public int lightDurationSec = 60;
    public boolean flashPrompt = true;
    public float flashIntervalSec = 1.0f;
    public boolean multiColorCycle = false;
    public boolean fixedColor = true;
    public int selectedColor = COLOR_GREEN; // 默认绿色
    public int inventoryDurationSec = 60;
    public int voltageWarningMv = 2600;
    public int signalFilterRssi = 90;

    private static AppConfig instance;

    public static synchronized AppConfig getInstance(Context context) {
        if (instance == null) {
            instance = new AppConfig();
            instance.load(context.getApplicationContext());
        }
        return instance;
    }

    public void load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        isBluetoothBase = sp.getBoolean("isBluetoothBase", true);
        isMiniBase = sp.getBoolean("isMiniBase", true);
        defaultBindInfo = sp.getBoolean("defaultBindInfo", true);
        fastBind = sp.getBoolean("fastBind", true);
        bindDelayMs = sp.getInt("bindDelayMs", 200);
        soundPrompt = sp.getBoolean("soundPrompt", true);
        lightDurationSec = sp.getInt("lightDurationSec", 60);
        flashPrompt = sp.getBoolean("flashPrompt", true);
        flashIntervalSec = sp.getFloat("flashIntervalSec", 1.0f);
        multiColorCycle = sp.getBoolean("multiColorCycle", false);
        fixedColor = sp.getBoolean("fixedColor", true);
        selectedColor = sp.getInt("selectedColor", COLOR_GREEN);
        inventoryDurationSec = sp.getInt("inventoryDurationSec", 60);
        voltageWarningMv = sp.getInt("voltageWarningMv", 2600);
        signalFilterRssi = sp.getInt("signalFilterRssi", 90);
    }

    public void save(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean("isBluetoothBase", isBluetoothBase)
                .putBoolean("isMiniBase", isMiniBase)
                .putBoolean("defaultBindInfo", defaultBindInfo)
                .putBoolean("fastBind", fastBind)
                .putInt("bindDelayMs", bindDelayMs)
                .putBoolean("soundPrompt", soundPrompt)
                .putInt("lightDurationSec", lightDurationSec)
                .putBoolean("flashPrompt", flashPrompt)
                .putFloat("flashIntervalSec", flashIntervalSec)
                .putBoolean("multiColorCycle", multiColorCycle)
                .putBoolean("fixedColor", fixedColor)
                .putInt("selectedColor", selectedColor)
                .putInt("inventoryDurationSec", inventoryDurationSec)
                .putInt("voltageWarningMv", voltageWarningMv)
                .putInt("signalFilterRssi", signalFilterRssi)
                .apply();
    }
}
