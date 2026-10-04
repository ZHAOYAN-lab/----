package com.beaconfinder.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.beaconfinder.app.model.BaseStation;
import com.beaconfinder.app.model.Beacon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DeviceStore {
    private static final String PREFS = "beacon_finder_devices";
    private static final String KEY_BASES = "bases";
    private static final String KEY_BEACONS = "beacons";

    private final SharedPreferences preferences;
    private final List<BaseStation> bases = new ArrayList<>();
    private final List<Beacon> beacons = new ArrayList<>();

    public DeviceStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public List<BaseStation> bases() { return bases; }
    public List<Beacon> beacons() { return beacons; }

    public List<Beacon> beaconsFor(String baseSn) {
        List<Beacon> result = new ArrayList<>();
        for (Beacon beacon : beacons) {
            if (beacon.baseSn.equals(baseSn)) result.add(beacon);
        }
        return result;
    }

    public void addBase(BaseStation base) {
        for (int i = 0; i < bases.size(); i++) {
            if (bases.get(i).sn.equalsIgnoreCase(base.sn)) {
                bases.set(i, base);
                save();
                return;
            }
        }
        bases.add(base);
        save();
    }

    public void removeBase(String sn) {
        for (Iterator<BaseStation> it = bases.iterator(); it.hasNext();) {
            if (it.next().sn.equals(sn)) it.remove();
        }
        for (Iterator<Beacon> it = beacons.iterator(); it.hasNext();) {
            if (it.next().baseSn.equals(sn)) it.remove();
        }
        save();
    }

    public void addBeacon(Beacon beacon) {
        for (int i = 0; i < beacons.size(); i++) {
            Beacon item = beacons.get(i);
            if (item.baseSn.equals(beacon.baseSn) && item.code.equals(beacon.code)) {
                beacons.set(i, beacon);
                save();
                return;
            }
        }
        beacons.add(beacon);
        save();
    }

    public void removeBeacon(Beacon beacon) {
        beacons.remove(beacon);
        save();
    }

    public Beacon findBeacon(String baseSn, String normalizedCode) {
        for (Beacon beacon : beacons) {
            if (beacon.baseSn.equals(baseSn) && beacon.code.equals(normalizedCode)) return beacon;
        }
        return null;
    }

    private void load() {
        try {
            JSONArray baseArray = new JSONArray(preferences.getString(KEY_BASES, "[]"));
            for (int i = 0; i < baseArray.length(); i++) {
                JSONObject item = baseArray.getJSONObject(i);
                bases.add(new BaseStation(item.getString("sn"), item.getString("name")));
            }
            JSONArray beaconArray = new JSONArray(preferences.getString(KEY_BEACONS, "[]"));
            for (int i = 0; i < beaconArray.length(); i++) {
                JSONObject item = beaconArray.getJSONObject(i);
                beacons.add(new Beacon(item.getString("code"), item.getString("name"), item.getString("baseSn")));
            }
        } catch (Exception ignored) {
            bases.clear();
            beacons.clear();
        }
    }

    public void save() {
        try {
            JSONArray baseArray = new JSONArray();
            for (BaseStation base : bases) {
                JSONObject item = new JSONObject();
                item.put("sn", base.sn);
                item.put("name", base.name);
                baseArray.put(item);
            }
            JSONArray beaconArray = new JSONArray();
            for (Beacon beacon : beacons) {
                JSONObject item = new JSONObject();
                item.put("code", beacon.code);
                item.put("name", beacon.name);
                item.put("baseSn", beacon.baseSn);
                beaconArray.put(item);
            }
            preferences.edit()
                    .putString(KEY_BASES, baseArray.toString())
                    .putString(KEY_BEACONS, beaconArray.toString())
                    .apply();
        } catch (Exception ignored) { }
    }
}
