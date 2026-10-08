package com.beaconfinder.app.sdk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.beaconfinder.app.MainActivity;
import com.beaconfinder.app.R;
import com.beaconfinder.app.data.DeviceStore;
import com.beaconfinder.app.data.SharedSync;
import com.beaconfinder.app.model.BaseStation;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persistent connected-device bridge: the Activity is not the command executor. */
public final class WarehouseBridgeService extends Service implements BaseStationGateway.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private BaseStationGateway gateway;
    private SharedSync sync;
    private JSONObject pending;
    private long claimedAt;
    private boolean claimed, destroyed;
    private android.os.PowerManager.WakeLock wakeLock;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private Runnable connectTimeout, receiptTimeout;
    private boolean retrying;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            sync.sync(null);
            retryReports();
            if (!claimed && pending == null && sync.configured() && !sync.hasConflict()) claim();
            main.postDelayed(this, 1200);
        }
    };
    private void retryReports() {
        if (retrying || !sync.configured()) return;
        java.util.Map<String, ?> reports = getSharedPreferences("bridge_reports", MODE_PRIVATE).getAll();
        if (reports.isEmpty()) return;
        retrying = true;
        network.execute(() -> {
            for (java.util.Map.Entry<String, ?> entry : reports.entrySet()) {
                try {
                    sync.request("tasks/" + entry.getKey() + "/report", new JSONObject((String) entry.getValue()));
                    getSharedPreferences("bridge_reports", MODE_PRIVATE).edit().remove(entry.getKey()).commit();
                } catch (Exception e) {
                    // A stopped task intentionally rejects a stale generation; do not retry forever.
                    if (e.getMessage() != null && e.getMessage().contains("旧回执"))
                        getSharedPreferences("bridge_reports", MODE_PRIVATE).edit().remove(entry.getKey()).commit();
                }
            }
            main.post(() -> retrying = false);
        });
    }
    public static void start(Context context) {
        if (!SharedSync.get(context).configured()) return;
        Intent intent = new Intent(context, WarehouseBridgeService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
    }
    public static boolean isBle(BaseStation b) {
        String value = (b.sn + " " + b.name).toLowerCase(Locale.ROOT);
        return value.contains("ble") || value.contains("bluetooth") || value.contains("flashfind") || value.contains("sanray");
    }
    @Override public void onCreate() {
        super.onCreate(); sync = SharedSync.get(this); gateway = BaseStationGateway.getInstance();
        com.beaconfinder.app.data.AppLanguage.locale(this);
        gateway.addListener(this);
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "BeaconFinder:WarehouseBridge");
        wakeLock.acquire();
        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiLock = wifi.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WarehouseBridge");
        wifiLock.acquire();
        String channel = "warehouse_bridge";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "仓库联动桥接", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, com.beaconfinder.app.ui.WarehouseMapActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        startForeground(8108, builder.setSmallIcon(R.drawable.ic_search).setContentTitle(com.beaconfinder.app.data.AppLanguage.text("仓库联动已开启"))
                .setContentText(com.beaconfinder.app.data.AppLanguage.text("同步 PC 与手机资料，接收信标寻找任务")).setContentIntent(open).setOngoing(true).build());
        main.post(poll);
    }
    private void claim() {
        claimed = true;
        JSONArray bases = new JSONArray();
        BluetoothStationManager ble = BluetoothStationManager.getInstance(this);
        for (BaseStation base : DeviceStore.getInstance(this).bases()) {
            // Advertise BLE capability only while its physical connection is live.
            if (!isBle(base) || ble.isConnected()) bases.put(base.sn);
        }
        network.execute(() -> {
            try {
                JSONObject task = sync.request("bridge/claim", new JSONObject().put("clientId", sync.clientId()).put("bases", bases));
                main.post(() -> { claimed = false; if (!destroyed && task.has("id")) execute(task); });
            } catch (Exception e) { main.post(() -> { claimed = false; sync.status = "桥接连接：" + e.getMessage(); }); }
        });
    }
    private void execute(JSONObject task) {
        pending = task;
        claimedAt = android.os.SystemClock.elapsedRealtime();
        String sn = task.optString("baseSn");
        BaseStation base = null;
        for (BaseStation candidate : DeviceStore.getInstance(this).bases()) if (candidate.sn.equals(sn)) base = candidate;
        if (base == null) { finish("failed", "桥接手机未登记目标基站"); return; }
        if (isBle(base)) { send(true); return; }
        if (sn.equalsIgnoreCase(gateway.connectedSn)) { send(false); return; }
        gateway.connect(sn);
        connectTimeout = () -> { if (pending != null) finish("failed", "LAN 基站连接超时"); };
        main.postDelayed(connectTimeout, 6500);
    }
    private void send(boolean bluetooth) {
        if (pending == null) return;
        if (connectTimeout != null) main.removeCallbacks(connectTimeout);
        JSONObject task = pending;
        try {
            boolean stop = task.optString("action").equals("stop");
            int duration = Math.min(task.getInt("durationSec"), (int) Math.floor((task.getLong("expiresAt") - task.getLong("serverTime")
                    - (android.os.SystemClock.elapsedRealtime() - claimedAt)) / 1000.0));
            if (!stop && duration < 3) { finish("failed", "寻找任务已到期"); return; }
            java.util.List<String> codes = Collections.singletonList(task.getString("beaconCode"));
            String result;
            if (bluetooth) {
                BluetoothStationManager ble = BluetoothStationManager.getInstance(this);
                if (!ble.isConnected()) { finish("failed", "蓝牙基站已断开"); return; }
                result = stop ? ble.stop(codes) : ble.light(codes, task.getInt("color"), task.getBoolean("flash"), task.getBoolean("beep"), duration, (float) task.getDouble("intervalSec"));
                finish(!"0".equals(result) ? "failed" : stop ? "stopped" : "sent",
                        "0".equals(result) ? (stop ? "蓝牙 SDK 已发送消灯指令" : "蓝牙 SDK 已发送点灯指令，实物状态需现场确认") : "蓝牙 SDK 返回：" + result);
            } else {
                result = stop ? gateway.stop(codes) : gateway.light(codes, task.getInt("color"), task.getBoolean("flash"), task.getBoolean("beep"), duration, (float) task.getDouble("intervalSec"));
                if (!"0".equals(result)) { finish("failed", "LAN SDK 返回：" + result); return; }
                report(task, stop ? "stopped" : "sent", stop ? "LAN SDK 已发送消灯指令" : "LAN SDK 已发送，等待基站回执");
                // ponytail: serialize one LAN command until its uncorrelated F4/F9 receipt arrives.
                receiptTimeout = () -> { pending = null; };
                main.postDelayed(receiptTimeout, 2500);
            }
        } catch (Exception e) { finish("failed", "硬件控制异常：" + e.getMessage()); }
    }
    private void report(JSONObject task, String status, String message) {
        network.execute(() -> {
            try {
                JSONObject payload = new JSONObject().put("clientId", sync.clientId()).put("generation", task.getInt("generation"))
                        .put("status", status).put("message", message);
                // Persist before sending: a lost HTTP reply must not cause a second physical light command.
                getSharedPreferences("bridge_reports", MODE_PRIVATE).edit().putString(task.getString("id"), payload.toString()).commit();
                sync.request("tasks/" + task.getString("id") + "/report", payload);
                getSharedPreferences("bridge_reports", MODE_PRIVATE).edit().remove(task.getString("id")).commit();
            } catch (Exception e) { sync.status = "硬件回执待同步：" + e.getMessage(); }
        });
    }
    private void finish(String status, String message) {
        if (pending == null) return;
        JSONObject task = pending; pending = null;
        if (connectTimeout != null) main.removeCallbacks(connectTimeout);
        if (receiptTimeout != null) main.removeCallbacks(receiptTimeout);
        report(task, status, message);
    }
    @Override public void onConnectionChanged(String sn, boolean online) {
        if (pending != null && pending.optString("baseSn").equalsIgnoreCase(sn)) {
            if (online) send(false); else finish("failed", "LAN 基站连接断开");
        }
    }
    @Override public void onCommandResult(boolean success, String message) {
        if (pending != null) finish(success ? pending.optString("action").equals("stop") ? "stopped" : "accepted" : "failed",
                success ? "基站已确认接收指令" : "基站拒绝了指令");
    }
    @Override public void onBeaconReport(String code, String state) { }
    @Override public void onRawMessage(String message) { }
    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null); gateway.removeListener(this); network.shutdown();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        super.onDestroy();
    }
}
