package com.beaconfinder.app.sdk;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.example.basesdk.BaseReader;

import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import RfidUtils.HandlerBaseMsgLog;
import RfidUtils.HandlerBaseStateChangeLog;
import RfidUtils.RfidHandle;
import UdpUtils.JsonDataClass.UdpNetConfigJsonData;
import UdpUtils.UdpHandle;
import UdpUtils.UdpRecvDataHandle;

public final class BaseStationGateway {
    private static final String TAG = "BaseStationGateway";

    public interface Listener {
        void onConnectionChanged(String sn, boolean online);
        void onCommandResult(boolean success, String message);
        void onBeaconReport(String code, String state);
        void onRawMessage(String message);
        default void onDiscoveredBase(String id, String ip, String port, String mac, boolean appControlStatus) {}
    }

    private final BaseReader reader = new BaseReader();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile String targetIdentifier = "";
    private volatile boolean isBroadcasting = false;

    public BaseStationGateway() {
        reader.onBaseStateChangeLog = new HandlerBaseStateChangeLog() {
            @Override public void baseStateChangeLog(String sn, Boolean online) {
                if (listener != null) {
                    mainHandler.post(() -> listener.onConnectionChanged(sn, Boolean.TRUE.equals(online)));
                }
            }
        };
        reader.onBaseMsgLog = new HandlerBaseMsgLog() {
            @Override public void baseMsgLog(String data) {
                handleMessage(data);
            }
        };

        installSmartUdpHandler();
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public String version() { return reader.sdkVersion(); }

    /**
     * ロケーターに接続を開始
     * target は SN、MAC、または IP アドレス (例: 192.168.1.50)
     */
    public void connect(String target) {
        if (target == null) target = "";
        target = target.trim();
        targetIdentifier = target;

        // もし IP アドレスまたは IP:PORT 形式の場合は直接 TCP 接続を試行
        if (isIpAddress(target)) {
            String ip = target;
            int port = 5000;
            if (target.contains(":")) {
                String[] parts = target.split(":");
                ip = parts[0];
                try { port = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
            }
            connectDirect(target, ip, port);
            return;
        }

        // 通常の SN または MAC の場合: SDK の connectBase を呼びつつ、サブネットブロードキャストを送信
        reader.connectBase(target);
        triggerSubnetBroadcast();
    }

    /**
     * 自動検索接続（SN 指定なし、最初に応答した許可ロケーターに接続）
     */
    public void connectAny() {
        targetIdentifier = "";
        reader.connectBase();
        triggerSubnetBroadcast();
    }

    /**
     * IP とポートを指定して直接 TCP 接続
     */
    public void connectDirect(String sn, String ip, int port) {
        try {
            Field snField = BaseReader.class.getDeclaredField("baseSN");
            snField.setAccessible(true);
            snField.set(reader, sn);

            Field filterField = BaseReader.class.getDeclaredField("filterBaseSN");
            filterField.setAccessible(true);
            filterField.set(reader, sn);

            Field rfidField = BaseReader.class.getDeclaredField("rfidHandle");
            rfidField.setAccessible(true);
            RfidHandle handle = (RfidHandle) rfidField.get(reader);
            if (handle != null) {
                RfidHandle.isReConnect = true;
                handle.connectReader(ip, String.valueOf(port));
                Log.i(TAG, "Direct TCP connection initiated to " + ip + ":" + port);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed direct TCP connect: " + e.getMessage(), e);
            // フォールバック
            reader.connectBase(sn);
        }
    }

    public void disconnect() {
        targetIdentifier = "";
        isBroadcasting = false;
        reader.disConnectBase();
    }

    /**
     * LAN 内の全ロケーターへ探索パケットをサブネットブロードキャスト送信
     */
    public void triggerSubnetBroadcast() {
        if (isBroadcasting) return;
        isBroadcasting = true;

        new Thread(() -> {
            try {
                for (int i = 0; i < 4; i++) {
                    if (!isBroadcasting) break;
                    sendBroadcastPacket("{\"SyC\":\"GetNetConfig\"}");
                    Thread.sleep(800);
                }
            } catch (Exception ignored) {
            } finally {
                isBroadcasting = false;
            }
        }).start();
    }

    private void sendBroadcastPacket(String payload) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            byte[] bytes = payload.getBytes("UTF-8");

            // 1. 全体ブロードキャスト
            try {
                socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), 9002));
            } catch (Exception ignored) {}

            // 2. 各ネットワークインターフェースのサブネットブロードキャスト
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface nif = interfaces.nextElement();
                    if (nif.isLoopback() || !nif.isUp()) continue;
                    for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
                        InetAddress broadcast = addr.getBroadcast();
                        if (broadcast != null) {
                            try {
                                socket.send(new DatagramPacket(bytes, bytes.length, broadcast, 9002));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "sendBroadcastPacket failed: " + e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Smart UDP ハンドラーを SDK の内部に組み込み、柔軟なマッチングと検出通知を実現
     */
    private void installSmartUdpHandler() {
        try {
            Field udpHandleField = BaseReader.class.getDeclaredField("udpHandle");
            udpHandleField.setAccessible(true);
            UdpHandle udpHandle = (UdpHandle) udpHandleField.get(reader);
            if (udpHandle != null) {
                Field handlerField = UdpHandle.class.getDeclaredField("udpRecvDataHandle");
                handlerField.setAccessible(true);
                handlerField.set(null, new SmartUdpRecvDataHandle(udpHandle, reader, this));
                Log.i(TAG, "SmartUdpRecvDataHandle successfully installed");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not install SmartUdpRecvDataHandle: " + e.getMessage());
        }
    }

    void handleDiscoveredBase(UdpNetConfigJsonData data) {
        if (data == null || data.ID == null) return;

        boolean appOk = "1".equals(data.AppControlStatu);
        if (listener != null) {
            mainHandler.post(() -> listener.onDiscoveredBase(data.ID, data.Ri, data.Rp, data.Mac, appOk));
        }

        String target = targetIdentifier;
        if (target != null && !target.isEmpty()) {
            boolean matched = false;
            // 1. 完全一致
            if (target.equals(data.ID)) matched = true;
            // 2. 大文字小文字不問一致
            else if (target.equalsIgnoreCase(data.ID)) matched = true;
            // 3. MAC アドレス一致
            else if (data.Mac != null && target.replace(":", "").replace("-", "").equalsIgnoreCase(data.Mac.replace(":", "").replace("-", ""))) matched = true;
            // 4. IP アドレス一致
            else if (data.Ri != null && (target.equals(data.Ri) || target.startsWith(data.Ri + ":"))) matched = true;
            // 5. 10桁バーコードやSNの部分一致/末尾一致/含有/数字部分一致
            else if (data.ID != null) {
                String cleanTarget = target.replaceAll("[^A-Za-z0-9]", "");
                String cleanId = data.ID.replaceAll("[^A-Za-z0-9]", "");
                String digitsTarget = target.replaceAll("\\D+", "");
                String digitsId = data.ID.replaceAll("\\D+", "");

                if (cleanId.equalsIgnoreCase(cleanTarget)
                        || cleanId.endsWith(cleanTarget)
                        || cleanTarget.endsWith(cleanId)
                        || cleanId.contains(cleanTarget)
                        || cleanTarget.contains(cleanId)) {
                    matched = true;
                } else if (!digitsTarget.isEmpty() && !digitsId.isEmpty()) {
                    if (digitsId.equals(digitsTarget)
                            || digitsId.endsWith(digitsTarget)
                            || digitsTarget.endsWith(digitsId)
                            || (digitsTarget.length() >= 8 && digitsId.contains(digitsTarget))) {
                        matched = true;
                    }
                }
            }

            if (matched) {
                Log.i(TAG, "Base station matched: target=" + target + ", discovered ID=" + data.ID + ", IP=" + data.Ri);
                try {
                    Field filterField = BaseReader.class.getDeclaredField("filterBaseSN");
                    filterField.setAccessible(true);
                    filterField.set(reader, data.ID);
                } catch (Exception ignored) {}

                // 直接TCP接続を実行して接続の確実性を担保
                if (appOk && data.Ri != null && !data.Ri.isEmpty()) {
                    int port = 5000;
                    try { port = Integer.parseInt(data.Rp); } catch (Exception ignored) {}
                    connectDirect(data.ID, data.Ri, port);
                }
            }
        }
    }

    private static boolean isIpAddress(String text) {
        return text != null && text.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?$");
    }

    public String light(List<String> codes, int color, boolean flash, boolean beep) {
        byte rgb = (byte) (flash ? color : (color | 0x80));
        return reader.SetAcoustOpticTagsWork(
                rgb,
                (byte) (beep ? 0x01 : 0x00),
                toTagIds(codes),
                (byte) 20,
                (byte) 5
        );
    }

    public String stop(List<String> codes) {
        return reader.SetAcoustOpticTagsWork(
                (byte) 0x08,
                (byte) 0x00,
                toTagIds(codes),
                (byte) 1,
                (byte) 1
        );
    }

    private List<byte[]> toTagIds(List<String> codes) {
        List<byte[]> result = new ArrayList<>();
        for (String code : codes) {
            long value = Long.parseLong(code);
            result.add(new byte[] {
                    (byte) (value >>> 24),
                    (byte) (value >>> 16),
                    (byte) (value >>> 8),
                    (byte) value
            });
        }
        return result;
    }

    private void handleMessage(String data) {
        if (listener == null || data == null) return;
        listener.onRawMessage(data);
        String[] fields = (data + ",0").split(",");
        if (fields.length < 3) return;
        String command = fields[1].trim().toUpperCase(Locale.ROOT);
        if ("F4".equals(command) || "F9".equals(command)) {
            boolean success = "1".equals(fields[2]);
            String message = success ? "ロケーターがコマンドを受信しました" : "ロケーターの実行に失敗しました";
            listener.onCommandResult(success, message);
        } else if ("F5".equals(command) && fields.length >= 12 && "1".equals(fields[2])) {
            String code = normalizeNumericCode(fields[3]);
            String state;
            if ("0".equals(fields[7]) && "0".equals(fields[8]) && "0".equals(fields[9])
                    && "0".equals(fields[10]) && "00000000".equals(fields[11])) {
                state = "オンライン · 電圧 " + fields[4] + "mV · 待機中";
            } else {
                state = "オンライン · 電圧 " + fields[4] + "mV · 動作中";
            }
            listener.onBeaconReport(code, state);
        }
    }

    public static String normalizeNumericCode(String input) {
        long value = Long.parseLong(input.trim());
        if (value < 0 || value > 0xFFFFFFFFL) throw new IllegalArgumentException("ビーコンコードが4バイト範囲を超えています");
        return String.format(java.util.Locale.US, "%010d", value);
    }

    /**
     * 内部カスタム UdpRecvDataHandle
     */
    private static class SmartUdpRecvDataHandle extends UdpRecvDataHandle {
        private final BaseStationGateway gateway;

        public SmartUdpRecvDataHandle(UdpHandle udpHandle, BaseReader baseReader, BaseStationGateway gateway) {
            super(udpHandle, baseReader);
            this.gateway = gateway;
        }

        @Override
        public void DataAnalysis(String msg) {
            try {
                UdpNetConfigJsonData data = JSON.parseObject(msg, UdpNetConfigJsonData.class);
                if (data != null && "GetNetConfigRsp".equals(data.SyC)) {
                    gateway.handleDiscoveredBase(data);
                }
            } catch (Exception e) {
                Log.w(TAG, "Smart DataAnalysis exception: " + e.getMessage());
            }
            super.DataAnalysis(msg);
        }
    }
}
