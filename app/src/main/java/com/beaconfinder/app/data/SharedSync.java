package com.beaconfinder.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.beaconfinder.app.sdk.BaseStationGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One server catalog; optimistic changes preserve edits made during a network request. */
public final class SharedSync {
    public static final String UPDATED = "com.beaconfinder.app.SHARED_UPDATED";
    public static final String[] KINDS = {"warehouses", "areas", "products", "bases", "beacons"};
    private static SharedSync instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private boolean busy;
    public volatile String status = "共享服务未配置";

    public static synchronized SharedSync get(Context context) {
        if (instance == null) instance = new SharedSync(context.getApplicationContext());
        return instance;
    }
    private SharedSync(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("shared_warehouse", Context.MODE_PRIVATE);
        if (!prefs.contains("clientId")) prefs.edit().putString("clientId", java.util.UUID.randomUUID().toString()).commit();
    }
    public String address() { return prefs.getString("address", ""); }
    public String token() { return prefs.getString("token", ""); }
    public String clientId() { return prefs.getString("clientId", ""); }
    public boolean configured() { return !address().isEmpty() && !token().isEmpty(); }
    public boolean hasConflict() { return prefs.contains("conflict_changes"); }
    public void configure(String address, String token) throws Exception {
        URL url = new URL(address.trim());
        if (!(url.getProtocol().equals("http") || url.getProtocol().equals("https")) || url.getHost().isEmpty()
                || url.getUserInfo() != null || !url.getPath().isEmpty() && !url.getPath().equals("/")) {
            throw new IllegalArgumentException("填写 http://电脑IP:8088 格式的服务地址");
        }
        if (token.trim().isEmpty()) throw new IllegalArgumentException("请填写连接密钥");
        String normalized = address.trim().replaceAll("/+$", "");
        if (!normalized.equals(address()) || !token.trim().equals(token())) {
            prefs.edit().remove("baseline").remove("conflict_changes").putString("address", normalized)
                    .putString("token", token.trim()).commit();
        }
    }
    public JSONObject request(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address() + "/api/" + path).openConnection();
        connection.setConnectTimeout(4000); connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("X-Access-Key", token());
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            java.io.InputStream input = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new Exception("服务响应 " + code);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (java.io.InputStream stream = input) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (output.size() + count > 8 * 1024 * 1024) throw new Exception("共享资料超出大小限制");
                    output.write(buffer, 0, count);
                }
            }
            String raw = output.toString("UTF-8");
            JSONObject result = raw.equals("null") ? new JSONObject() : new JSONObject(raw);
            if (code == 409) throw new SyncConflict(result.optString("error"));
            if (code >= 400) throw new Exception(result.optString("error", "服务响应 " + code));
            return result;
        } finally { connection.disconnect(); }
    }
    private static class SyncConflict extends Exception { SyncConflict(String message) { super(message); } }

    private JSONObject local() throws Exception {
        JSONObject result = WarehouseStore.getInstance(context).snapshot();
        JSONObject devices = DeviceStore.getInstance(context).snapshot();
        result.put("bases", devices.getJSONArray("bases")).put("beacons", devices.getJSONArray("beacons"));
        return result;
    }
    private static JSONObject index(JSONArray array) throws Exception {
        JSONObject result = new JSONObject();
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i); result.put(item.getString("id"), item);
        }
        return result;
    }
    private static String canonical(Object object) throws Exception {
        if (!(object instanceof JSONObject)) return String.valueOf(object);
        JSONObject value = (JSONObject) object;
        java.util.List<String> keys = new java.util.ArrayList<>();
        Iterator<String> iterator = value.keys();
        while (iterator.hasNext()) { String key = iterator.next(); if (!key.equals("version")) keys.add(key); }
        java.util.Collections.sort(keys);
        StringBuilder result = new StringBuilder();
        for (String key : keys) result.append(JSONObject.quote(key)).append(':').append(JSONObject.quote(String.valueOf(value.get(key)))).append(';');
        return result.toString();
    }
    private static JSONArray diff(JSONObject local, JSONObject baseline) throws Exception {
        JSONArray changes = new JSONArray();
        for (String kind : KINDS) {
            JSONObject now = index(local.optJSONArray(kind)), before = index(baseline.optJSONArray(kind));
            Iterator<String> it = now.keys();
            while (it.hasNext()) {
                String id = it.next(); JSONObject item = now.getJSONObject(id), old = before.optJSONObject(id);
                if (old == null || !canonical(item).equals(canonical(old))) {
                    changes.put(new JSONObject().put("kind", kind).put("id", id).put("data", item)
                            .put("expectedVersion", old == null ? 0 : old.optLong("version")));
                }
            }
            it = before.keys();
            while (it.hasNext()) {
                String id = it.next();
                if (!now.has(id)) changes.put(new JSONObject().put("kind", kind).put("id", id).put("delete", true)
                        .put("expectedVersion", before.getJSONObject(id).optLong("version")));
            }
        }
        return changes;
    }
    private static JSONObject overlay(JSONObject remote, JSONArray changes) throws Exception {
        JSONObject result = new JSONObject(remote.toString());
        for (String kind : KINDS) {
            JSONObject items = index(result.optJSONArray(kind));
            for (int i = 0; i < changes.length(); i++) {
                JSONObject change = changes.getJSONObject(i);
                if (!kind.equals(change.getString("kind"))) continue;
                if (change.optBoolean("delete")) items.remove(change.getString("id"));
                else items.put(change.getString("id"), change.getJSONObject("data"));
            }
            JSONArray array = new JSONArray(); Iterator<String> it = items.keys();
            while (it.hasNext()) array.put(items.get(it.next()));
            result.put(kind, array);
        }
        return result;
    }
    private void apply(JSONObject result) throws Exception {
        WarehouseStore.getInstance(context).applySnapshot(result);
        DeviceStore.getInstance(context).applySnapshot(result);
        context.sendBroadcast(new android.content.Intent(UPDATED).setPackage(context.getPackageName()));
    }
    public void sync(Runnable done) {
        if (!configured() || busy) { if (done != null) done.run(); return; }
        try {
            final JSONObject captured = local();
            final JSONObject baseline = new JSONObject(prefs.getString("baseline", "{}"));
            final JSONArray changes = diff(captured, baseline);
            busy = true;
            network.execute(() -> {
                try {
                    JSONObject remote;
                    try { remote = hasConflict() || changes.length() == 0 ? request("state", null)
                            : request("sync", new JSONObject().put("changes", changes)); }
                    catch (SyncConflict conflict) {
                        prefs.edit().putString("conflict_changes", changes.toString()).commit();
                        remote = request("state", null);
                    }
                    final JSONObject result = remote;
                    main.post(() -> {
                        try {
                            if (!hasConflict()) {
                                JSONArray duringRequest = diff(local(), captured);
                                JSONObject merged = overlay(result, duringRequest);
                                if (diff(merged, local()).length() > 0) apply(merged);
                                prefs.edit().putString("baseline", result.toString()).commit();
                                status = "已同步";
                            } else {
                                prefs.edit().putString("conflict_remote", result.toString()).commit();
                                status = "同步冲突：本地修改已保留，请在地图连接页处理";
                            }
                        } catch (Exception e) { status = e.getMessage(); }
                        busy = false; if (done != null) done.run();
                    });
                } catch (Exception e) {
                    main.post(() -> { status = "同步失败：" + e.getMessage(); busy = false; if (done != null) done.run(); });
                }
            });
        } catch (Exception e) { status = e.getMessage(); if (done != null) done.run(); }
    }
    public void resolveConflict(boolean keepLocal) throws Exception {
        JSONObject remote = new JSONObject(prefs.getString("conflict_remote", "{}"));
        // Capture ALL current edits, including changes made after the original conflict.
        JSONObject before = new JSONObject(prefs.getString("baseline", "{}"));
        JSONArray draft = diff(local(), before);
        prefs.edit().putString("last_conflict_backup", local().toString()).commit();
        apply(keepLocal ? overlay(remote, draft) : remote);
        prefs.edit().putString("baseline", remote.toString()).remove("conflict_changes").remove("conflict_remote").commit();
        sync(null);
    }
    public void find(java.util.List<com.beaconfinder.app.model.Beacon> beacons, boolean stop,
                     int color, boolean flash, boolean beep) {
        sync(() -> network.execute(() -> {
            try {
                if (hasConflict()) throw new Exception("请先处理资料同步冲突");
                AppConfig config = AppConfig.getInstance(context);
                JSONObject current = request("state", null);
                for (com.beaconfinder.app.model.Beacon beacon : beacons) {
                    if (stop) {
                        JSONArray tasks = current.getJSONArray("tasks");
                        JSONObject latest = null;
                        for (int i = 0; i < tasks.length(); i++) { JSONObject task = tasks.getJSONObject(i);
                            if (beacon.code.equals(task.optString("beaconCode")) && (latest == null || task.optLong("createdAt") > latest.optLong("createdAt"))) latest = task;
                        }
                        if (latest == null) throw new Exception("没有可停止的寻找任务");
                        request("tasks/" + latest.getString("id") + "/stop", new JSONObject());
                    } else {
                        JSONObject body = new JSONObject().put("beaconCode", beacon.code).put("baseSn", beacon.baseSn)
                                .put("origin", "phone").put("color", color).put("flash", flash).put("beep", beep)
                                .put("durationSec", config.lightDurationSec).put("intervalSec", config.flashIntervalSec);
                        JSONArray products = current.getJSONArray("products");
                        for (int i = 0; i < products.length(); i++) { JSONObject p = products.getJSONObject(i);
                            if (beacon.code.equals(p.optString("beaconCode"))) body.put("productId", p.getString("id"));
                        }
                        request("find", body);
                    }
                }
                main.post(() -> Toast.makeText(context, AppLanguage.text(stop ? "已提交同步停止" : "已提交联动寻找，请在地图查看位置与执行状态"), Toast.LENGTH_SHORT).show());
            } catch (Exception e) { main.post(() -> Toast.makeText(context, AppLanguage.text("寻找失败：" + e.getMessage()), Toast.LENGTH_LONG).show()); }
        }));
    }
}
