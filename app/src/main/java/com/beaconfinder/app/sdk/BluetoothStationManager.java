package com.beaconfinder.app.sdk;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import feasyblue.BleStateMessage;
import feasyblue.BleStateTypes;
import typehelper.DevTypes;
import uhf.AsyncSocketState;
import uhf.MultiLableCallBack;
import uhf.Reader;

/**
 * Bluetoothポータブルロケーターマネージャー
 * SDK公式Demo（RfidManager + UpdateBleStateTimer）に完全準拠した実装
 *
 * BLE接続フロー:
 *   1. GPS(位置情報)サービスが有効か確認
 *   2. BLE権限を確認・要求
 *   3. Bluetoothが有効か確認
 *   4. connectBle("XY-FlashFind", true, -90) を呼び出す
 *   5. 定時タイマー(1秒間隔)で getBleStateMessage() をポーリング
 *   6. Connected かつ GetClientInfo().size() > 0 になったら rfidClient を取得
 *   7. rfidClient を使って SetAcoustOpticTagsWork() で点灯制御
 */
public final class BluetoothStationManager implements MultiLableCallBack {

    private static final String TAG = "BluetoothStationMgr";

    /** SDK固定BLE名 (Sanrayポータブルロケーターは常にこの名前でアドバタイズ) */
    public static final String DEFAULT_BLE_NAME = "XY-FlashFind";

    public interface Listener {
        void onBleStateChanged(int state, int rssi, String stateDesc);
        void onDeviceFound(String name, String address, int rssi);
        void onMessageReceived(String data);
        void onLog(String message);
    }

    private static BluetoothStationManager instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** SDK メインクラス */
    private Reader readerController;

    /**
     * 接続中のロケータークライアント
     * 公式Demo: Connected && GetClientInfo().size() > 0 の時にのみ更新
     */
    private volatile AsyncSocketState rfidClient;

    private Listener listener;

    /** BLE状態ポーリングタイマー (公式Demo: UpdateBleStateTimer 相当, 1秒間隔) */
    private Timer pollTimer;
    private volatile boolean pollRunning = false;

    /** 周辺デバイス探索用 */
    private boolean isScanning = false;
    private BluetoothLeScanner leScanner;
    private ScanCallback scanCallback;

    private int currentFilterRssi = -90;

    // ── シングルトン ──────────────────────────────────────────────────────────

    private BluetoothStationManager(Context context) {
        this.context = context.getApplicationContext();
        initSdk();
    }

    public static synchronized BluetoothStationManager getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothStationManager(context);
        }
        return instance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ── SDK初期化 ──────────────────────────────────────────────────────────────

    /**
     * SDK初期化 (公式Demo: RfidManager.init() 相当)
     * Reader生成 → SetDevMode(Module) → initBleHandle(context)
     */
    private void initSdk() {
        try {
            readerController = new Reader(this);
            readerController.SetDevMode(DevTypes.Module);
            readerController.initBleHandle(context);
            log("Bluetooth SDK初期化完了");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BLE SDK", e);
            log("Bluetooth SDK初期化失敗: " + e.getMessage());
        }
    }

    // ── 接続管理 ──────────────────────────────────────────────────────────────

    /**
     * BLE接続開始
     * 公式Demo: ReaderController.connectBle("XY-FlashFind", true, -90)
     */
    public synchronized void connect(int filterRssi) {
        if (readerController == null) initSdk();
        this.currentFilterRssi = filterRssi;
        log("BLE接続開始: " + DEFAULT_BLE_NAME + ", RSSI=" + filterRssi + "dBm");
        try {
            readerController.connectBle(DEFAULT_BLE_NAME, true, filterRssi);
            startStatePolling();
        } catch (Exception e) {
            Log.e(TAG, "connectBle failed", e);
            log("BLE接続エラー: " + e.getMessage());
        }
    }

    /** 後方互換: 名前指定付き (名前は無視し常に XY-FlashFind で接続) */
    public synchronized void connect(String bleName, boolean reconnect, int filterRssi) {
        connect(filterRssi);
    }

    /**
     * BLE切断
     * 公式Demo: ReaderController.disConnectBle()
     */
    public synchronized void disconnect() {
        log("BLE切断処理中…");
        stopStatePolling();
        rfidClient = null;
        if (readerController != null) {
            try { readerController.disConnectBle(); } catch (Exception ignored) {}
        }
        notifyState(BleStateTypes.DisConnected, 0);
        log("BLE切断完了");
    }

    /** メイン画面起動時の自動接続 */
    public void autoConnect(int filterRssi) {
        if (isConnected()) return;
        connect(filterRssi);
    }

    // ── 接続状態 ──────────────────────────────────────────────────────────────

    /**
     * 接続済みか判定
     * 公式Demo: bleState==Connected && GetClientInfo().size()>0 && rfidClient!=null
     */
    public boolean isConnected() {
        if (readerController == null) return false;
        try {
            BleStateMessage msg = readerController.getBleStateMessage();
            return msg != null
                    && msg.bleState == BleStateTypes.Connected
                    && rfidClient != null;
        } catch (Exception e) {
            return false;
        }
    }

    public AsyncSocketState getClient() { return rfidClient; }

    // ── BLE状態ポーリングタイマー ────────────────────────────────────────────
    // 公式Demo: UpdateBleStateTimer.timerMethod() に完全準拠 (1秒間隔)

    private void startStatePolling() {
        stopStatePolling();
        pollRunning = true;
        pollTimer = new Timer("BleStatePoll");
        pollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!pollRunning || readerController == null) return;
                try {
                    BleStateMessage msg = readerController.getBleStateMessage();
                    if (msg == null) return;

                    // 公式Demo timerMethod() 準拠: Connected && size>0 → rfidClient更新
                    if (msg.bleState == BleStateTypes.Connected) {
                        List<AsyncSocketState> clients = readerController.GetClientInfo();
                        if (clients != null && !clients.isEmpty()) {
                            rfidClient = clients.get(0);
                        }
                    } else if (msg.bleState == BleStateTypes.DisConnected) {
                        rfidClient = null;
                    }

                    notifyState(msg.bleState, msg.bleRssi);
                } catch (Exception e) {
                    Log.w(TAG, "BLE state polling error: " + e.getMessage());
                }
            }
        }, 200, 1000); // 公式Demo: startTimer(1000)
    }

    private void stopStatePolling() {
        pollRunning = false;
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
    }

    private void notifyState(int state, int rssi) {
        String desc;
        switch (state) {
            case BleStateTypes.Scaning:    desc = "スキャン中… (XY-FlashFind)"; break;
            case BleStateTypes.Connecting: desc = "接続試行中…"; break;
            case BleStateTypes.Connected:  desc = "接続完了 (" + rssi + " dBm)"; break;
            default:                       desc = "未接続"; break;
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onBleStateChanged(state, rssi, desc);
        });
    }

    // ── 周辺BLEデバイス探索 ──────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public synchronized void startScan() {
        if (isScanning) return;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                log("Bluetoothが無効です。端末のBluetoothをONにしてください");
                return;
            }
            leScanner = adapter.getBluetoothLeScanner();
            if (leScanner == null) {
                log("BLEスキャナを取得できませんでした");
                return;
            }
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (result == null || result.getDevice() == null) return;
                    BluetoothDevice device = result.getDevice();
                    String name = device.getName();
                    String address = device.getAddress();
                    int rssi = result.getRssi();
                    mainHandler.post(() -> {
                        if (listener != null) listener.onDeviceFound(name, address, rssi);
                    });
                }
                @Override
                public void onScanFailed(int errorCode) {
                    log("スキャンエラー: コード " + errorCode);
                    isScanning = false;
                }
            };
            isScanning = true;
            leScanner.startScan(scanCallback);
            log("周辺BLEデバイスのスキャン開始");
        } catch (Exception e) {
            Log.e(TAG, "startScan error", e);
            log("スキャン開始失敗: " + e.getMessage());
            isScanning = false;
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized void stopScan() {
        if (!isScanning) return;
        try {
            if (leScanner != null && scanCallback != null) leScanner.stopScan(scanCallback);
            log("スキャン停止");
        } catch (Exception ignored) {}
        isScanning = false;
    }

    public boolean isScanning() { return isScanning; }

    public void setFilterRssi(int filterRssi) {
        this.currentFilterRssi = filterRssi;
        if (readerController != null) {
            try { readerController.setBleFilterRssi(filterRssi); } catch (Exception ignored) {}
        }
    }

    // ── 点灯・消灯制御 ──────────────────────────────────────────────────────

    /** ビーコン点灯 (デフォルト: 60秒、1秒間隔) */
    public String light(List<String> codes, int color, boolean flash, boolean beep) {
        return light(codes, color, flash, beep, 60, 1.0f);
    }

    /**
     * ビーコン点灯
     * 公式Demo: SetAcoustOpticTagsWork(rfidClient, rgb, beep, tagIds, workTime, intervalTime)
     *
     * @param color     AppConfig.COLOR_* 定数
     * @param flash     フラッシュ(点滅)有無
     * @param beep      ビープ音有無
     * @param workTimeSec  点灯時間(秒) ※SDK単位=3秒 → /3
     * @param intervalSec  フラッシュ間隔(秒) ※SDK単位=100ms → *10
     */
    public String light(List<String> codes, int color, boolean flash, boolean beep,
                        int workTimeSec, float intervalSec) {
        if (rfidClient == null) {
            log("点灯失敗: ロケーター未接続");
            return "-1";
        }
        try {
            // flash=false(常時点灯)の場合はbit7を立てる
            byte rgb = flash ? (byte) color : (byte) (color | 0x80);
            byte beepByte = (byte) (beep ? 0x01 : 0x00);
            byte wt = (byte) Math.max(1, Math.min(127, workTimeSec / 3));
            byte it = (byte) Math.max(1, Math.min(127, (int) (intervalSec * 10)));
            List<byte[]> tagIds = toTagIds(codes);
            String result = readerController.SetAcoustOpticTagsWork(rfidClient, rgb, beepByte, tagIds, wt, it);
            log("点灯送信 → " + result + " (" + codes.size() + "件)");
            return result;
        } catch (Exception e) {
            log("点灯エラー: " + e.getMessage());
            return "-1";
        }
    }

    /** ビーコン消灯 */
    public String stop(List<String> codes) {
        if (rfidClient == null) return "-1";
        try {
            List<byte[]> tagIds = toTagIds(codes);
            String result = readerController.SetAcoustOpticTagsWork(
                    rfidClient, (byte) 0x08, (byte) 0x00, tagIds, (byte) 1, (byte) 1);
            log("消灯送信 → " + result);
            return result;
        } catch (Exception e) {
            log("消灯エラー: " + e.getMessage());
            return "-1";
        }
    }

    public String testLightAll() {
        if (rfidClient == null) return "-1";
        List<byte[]> all = new ArrayList<>();
        all.add(new byte[]{0x00, 0x00, 0x00, 0x00});
        return readerController.SetAcoustOpticTagsWork(rfidClient, (byte) 0x04, (byte) 0x01, all, (byte) 5, (byte) 2);
    }

    public String testStopAll() {
        if (rfidClient == null) return "-1";
        List<byte[]> all = new ArrayList<>();
        all.add(new byte[]{0x00, 0x00, 0x00, 0x00});
        return readerController.SetAcoustOpticTagsWork(rfidClient, (byte) 0x08, (byte) 0x00, all, (byte) 1, (byte) 1);
    }

    public String getFirmwareVersion() {
        if (rfidClient == null) return "未接続";
        return readerController.GetFirmVersion(rfidClient);
    }

    public String getHardwareVersion() {
        if (rfidClient == null) return "未接続";
        return readerController.GetHardVersion(rfidClient);
    }

    public String setTagHeart(int seconds) {
        if (rfidClient == null) return "-1";
        return readerController.SetAcoustOpticTagsHeart(rfidClient, seconds);
    }

    public String setTagSleepTime(int minutes) {
        if (rfidClient == null) return "-1";
        return readerController.SetTagsSleepTime(rfidClient, minutes);
    }

    // ── ユーティリティ ──────────────────────────────────────────────────────

    /**
     * ビーコン番号(10進数)を4バイト配列に変換
     * 公式Demo: Conversion.longToBytes(Long.parseLong(tagID)) の末尾4バイト相当
     */
    private List<byte[]> toTagIds(List<String> codes) {
        List<byte[]> result = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            result.add(new byte[]{0x00, 0x00, 0x00, 0x00});
            return result;
        }
        for (String code : codes) {
            try {
                long value = Long.parseLong(code.trim());
                result.add(new byte[]{
                        (byte) (value >>> 24),
                        (byte) (value >>> 16),
                        (byte) (value >>> 8),
                        (byte) value
                });
            } catch (Exception ignored) {
                Log.w(TAG, "Invalid beacon code: " + code);
            }
        }
        if (result.isEmpty()) result.add(new byte[]{0x00, 0x00, 0x00, 0x00});
        return result;
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        mainHandler.post(() -> {
            if (listener != null) listener.onLog(msg);
        });
    }

    // ── MultiLableCallBack ─────────────────────────────────────────────────

    @Override
    public void method(String data) {
        log("ロケーター受信: " + data);
        mainHandler.post(() -> {
            if (listener != null) listener.onMessageReceived(data);
        });
    }

    @Override
    public void ReaderNotice(String noticeMsg) {
        log("ロケーター通知: " + noticeMsg);
    }
}
