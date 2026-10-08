package com.beaconfinder.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.beaconfinder.app.model.Area;
import com.beaconfinder.app.model.Product;
import com.beaconfinder.app.model.Warehouse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class WarehouseStore {
    private static final String PREFS = "beacon_finder_warehouse_prefs";
    private static final String KEY_WAREHOUSES = "warehouses";
    private static final String KEY_AREAS = "areas";
    private static final String KEY_PRODUCTS = "products";

    private static volatile WarehouseStore instance;

    private final SharedPreferences preferences;
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();

    public static WarehouseStore getInstance(Context context) {
        if (instance == null) {
            synchronized (WarehouseStore.class) {
                if (instance == null) {
                    instance = new WarehouseStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private WarehouseStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        if (warehouses.isEmpty()) {
            initDefaultData();
        }
    }

    private synchronized void initDefaultData() {
        Warehouse defaultWh = new Warehouse("wh_default", "本社倉庫", System.currentTimeMillis());
        warehouses.add(defaultWh);

        Area areaA = new Area("area_a1", "wh_default", "A棚-01", System.currentTimeMillis());
        Area areaB = new Area("area_b2", "wh_default", "B棚-02", System.currentTimeMillis());
        areas.add(areaA);
        areas.add(areaB);

        Product sampleProduct = new Product(
                "prod_sample_1",
                "サンプル部品A",
                "SKU-1001",
                "wh_default",
                "area_a1",
                "",
                "初期登録サンプル商品",
                System.currentTimeMillis()
        );
        products.add(sampleProduct);
        save();
    }

    // ─────────────────────────────────────────────────────────────
    // 倉庫 (Warehouse)
    // ─────────────────────────────────────────────────────────────

    public synchronized List<Warehouse> getWarehouses() {
        return new ArrayList<>(warehouses);
    }

    public synchronized Warehouse getWarehouse(String id) {
        if (id == null) return null;
        for (Warehouse w : warehouses) {
            if (id.equals(w.id)) return w;
        }
        return null;
    }

    public synchronized String getWarehouseName(String id) {
        Warehouse w = getWarehouse(id);
        return w != null ? w.name : "";
    }

    public synchronized void saveWarehouse(Warehouse warehouse) {
        if (warehouse.id == null || warehouse.id.trim().isEmpty()) {
            warehouse.id = "wh_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (warehouse.createdAt == 0) {
            warehouse.createdAt = System.currentTimeMillis();
        }

        for (int i = 0; i < warehouses.size(); i++) {
            if (warehouses.get(i).id.equals(warehouse.id)) {
                warehouses.set(i, warehouse);
                save();
                return;
            }
        }
        warehouses.add(warehouse);
        save();
    }

    public synchronized void deleteWarehouse(String id) {
        for (Iterator<Warehouse> it = warehouses.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(id)) {
                it.remove();
                break;
            }
        }
        // 所属するエリアと商品の倉庫紐付けをクリア
        for (Iterator<Area> it = areas.iterator(); it.hasNext(); ) {
            if (id.equals(it.next().warehouseId)) {
                it.remove();
            }
        }
        for (Product p : products) {
            if (id.equals(p.warehouseId)) {
                p.warehouseId = "";
                p.areaId = ""; p.shelf = "";
            }
        }
        save();
    }

    // ─────────────────────────────────────────────────────────────
    // エリア (Area)
    // ─────────────────────────────────────────────────────────────

    public synchronized List<Area> getAreas() {
        return new ArrayList<>(areas);
    }

    public synchronized List<Area> getAreasForWarehouse(String warehouseId) {
        List<Area> result = new ArrayList<>();
        if (warehouseId == null || warehouseId.isEmpty()) return result;
        for (Area a : areas) {
            if (warehouseId.equals(a.warehouseId)) {
                result.add(a);
            }
        }
        return result;
    }

    public synchronized Area getArea(String id) {
        if (id == null) return null;
        for (Area a : areas) {
            if (id.equals(a.id)) return a;
        }
        return null;
    }

    public synchronized String getAreaName(String id) {
        Area a = getArea(id);
        return a != null ? a.name : "";
    }

    public synchronized void saveArea(Area area) {
        if (area.id == null || area.id.trim().isEmpty()) {
            area.id = "area_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (area.createdAt == 0) {
            area.createdAt = System.currentTimeMillis();
        }

        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i).id.equals(area.id)) {
                areas.set(i, area);
                save();
                return;
            }
        }
        areas.add(area);
        save();
    }

    public synchronized void deleteArea(String id) {
        for (Iterator<Area> it = areas.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(id)) {
                it.remove();
                break;
            }
        }
        for (Product p : products) {
            if (id.equals(p.areaId)) {
                p.areaId = ""; p.shelf = "";
            }
        }
        save();
    }

    // ─────────────────────────────────────────────────────────────
    // 商品 (Product)
    // ─────────────────────────────────────────────────────────────

    public synchronized List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public synchronized Product getProduct(String id) {
        if (id == null) return null;
        for (Product p : products) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    public synchronized Product getProductByBeaconCode(String beaconCode) {
        if (beaconCode == null || beaconCode.trim().isEmpty()) return null;
        String normalized = beaconCode.trim();
        for (Product p : products) {
            if (p.beaconCode != null && p.beaconCode.trim().equalsIgnoreCase(normalized)) {
                return p;
            }
        }
        return null;
    }

    public synchronized void saveProduct(Product product) {
        if (product.id == null || product.id.trim().isEmpty()) {
            product.id = "prod_" + UUID.randomUUID().toString().substring(0, 8);
        }
        product.updatedAt = System.currentTimeMillis();

        // ビーコンが重複して紐付かないように既存の同一ビーコン紐付けをクリア
        if (product.beaconCode != null && !product.beaconCode.trim().isEmpty()) {
            String bCode = product.beaconCode.trim();
            for (Product p : products) {
                if (!p.id.equals(product.id) && bCode.equalsIgnoreCase(p.beaconCode)) {
                    p.beaconCode = "";
                }
            }
        }

        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).id.equals(product.id)) {
                products.set(i, product);
                save();
                return;
            }
        }
        products.add(product);
        save();
    }

    public synchronized void deleteProduct(String id) {
        for (Iterator<Product> it = products.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(id)) {
                it.remove();
                break;
            }
        }
        save();
    }

    public synchronized void bindProductToBeacon(String productId, String beaconCode) {
        String normalized = (beaconCode != null) ? beaconCode.trim() : "";
        for (Product p : products) {
            if (p.id.equals(productId)) {
                p.beaconCode = normalized;
                p.updatedAt = System.currentTimeMillis();
            } else if (!normalized.isEmpty() && normalized.equalsIgnoreCase(p.beaconCode)) {
                p.beaconCode = "";
            }
        }
        save();
    }

    public synchronized void unbindProduct(String productId) {
        for (Product p : products) {
            if (p.id.equals(productId)) {
                p.beaconCode = "";
                p.updatedAt = System.currentTimeMillis();
                break;
            }
        }
        save();
    }

    public synchronized void unbindBeacon(String beaconCode) {
        if (beaconCode == null || beaconCode.trim().isEmpty()) return;
        String normalized = beaconCode.trim();
        for (Product p : products) {
            if (p.beaconCode != null && p.beaconCode.trim().equalsIgnoreCase(normalized)) {
                p.beaconCode = "";
                p.updatedAt = System.currentTimeMillis();
            }
        }
        save();
    }

    public synchronized List<Product> searchProducts(String query, String warehouseId, String areaId) {
        List<Product> result = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";

        for (Product p : products) {
            if (warehouseId != null && !warehouseId.isEmpty() && !warehouseId.equals(p.warehouseId)) {
                continue;
            }
            if (areaId != null && !areaId.isEmpty() && !areaId.equals(p.areaId)) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean matchName = p.name != null && p.name.toLowerCase().contains(q);
                boolean matchSku = p.sku != null && p.sku.toLowerCase().contains(q);
                boolean matchBeacon = p.beaconCode != null && p.beaconCode.toLowerCase().contains(q);
                boolean matchMemo = p.memo != null && p.memo.toLowerCase().contains(q);
                if (!matchName && !matchSku && !matchBeacon && !matchMemo && !(p.shelf != null && p.shelf.toLowerCase().contains(q))) {
                    continue;
                }
            }
            result.add(p);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 永続化 (Load & Save)
    // ─────────────────────────────────────────────────────────────

    private void load() {
        try {
            JSONArray whArray = new JSONArray(preferences.getString(KEY_WAREHOUSES, "[]"));
            warehouses.clear();
            for (int i = 0; i < whArray.length(); i++) {
                JSONObject o = whArray.getJSONObject(i);
                warehouses.add(new Warehouse(
                        o.getString("id"),
                        o.getString("name"),
                        o.optLong("createdAt", 0)
                ));
            }

            JSONArray areaArray = new JSONArray(preferences.getString(KEY_AREAS, "[]"));
            areas.clear();
            for (int i = 0; i < areaArray.length(); i++) {
                JSONObject o = areaArray.getJSONObject(i);
                areas.add(new Area(
                        o.getString("id"),
                        o.getString("warehouseId"),
                        o.getString("name"),
                        o.optLong("createdAt", 0)
                ));
            }

            JSONArray prodArray = new JSONArray(preferences.getString(KEY_PRODUCTS, "[]"));
            products.clear();
            for (int i = 0; i < prodArray.length(); i++) {
                JSONObject o = prodArray.getJSONObject(i);
                Product loaded = new Product(
                        o.getString("id"),
                        o.getString("name"),
                        o.optString("sku", ""),
                        o.optString("warehouseId", ""),
                        o.optString("areaId", ""),
                        o.optString("beaconCode", ""),
                        o.optString("memo", ""),
                        o.optLong("updatedAt", 0)
                );
                loaded.shelf = o.optString("shelf", ""); products.add(loaded);
            }
        } catch (Exception ignored) {
            warehouses.clear();
            areas.clear();
            products.clear();
        }
    }

    public synchronized void save() {
        try {
            JSONArray whArray = new JSONArray();
            for (Warehouse w : warehouses) {
                JSONObject o = new JSONObject();
                o.put("id", w.id);
                o.put("name", w.name);
                o.put("createdAt", w.createdAt);
                whArray.put(o);
            }

            JSONArray areaArray = new JSONArray();
            for (Area a : areas) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("warehouseId", a.warehouseId);
                o.put("name", a.name);
                o.put("createdAt", a.createdAt);
                areaArray.put(o);
            }

            JSONArray prodArray = new JSONArray();
            for (Product p : products) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("sku", p.sku != null ? p.sku : "");
                o.put("warehouseId", p.warehouseId != null ? p.warehouseId : "");
                o.put("areaId", p.areaId != null ? p.areaId : "");
                o.put("shelf", p.shelf != null ? p.shelf : "");
                o.put("beaconCode", p.beaconCode != null ? p.beaconCode : "");
                o.put("memo", p.memo != null ? p.memo : "");
                o.put("updatedAt", p.updatedAt);
                prodArray.put(o);
            }

            preferences.edit()
                    .putString(KEY_WAREHOUSES, whArray.toString())
                    .putString(KEY_AREAS, areaArray.toString())
                    .putString(KEY_PRODUCTS, prodArray.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public synchronized JSONObject snapshot() throws Exception {
        JSONObject result = new JSONObject();
        result.put("warehouses", new JSONArray(preferences.getString(KEY_WAREHOUSES, "[]")));
        result.put("areas", new JSONArray(preferences.getString(KEY_AREAS, "[]")));
        result.put("products", new JSONArray(preferences.getString(KEY_PRODUCTS, "[]")));
        return result;
    }

    public synchronized void applySnapshot(JSONObject snapshot) throws Exception {
        // Keep the original phone catalog before the first server migration.
        if (!preferences.contains("migration_backup")) {
            preferences.edit().putString("migration_backup", snapshot().toString()).commit();
        }
        preferences.edit().putString(KEY_WAREHOUSES, snapshot.getJSONArray("warehouses").toString())
                .putString(KEY_AREAS, snapshot.getJSONArray("areas").toString())
                .putString(KEY_PRODUCTS, snapshot.getJSONArray("products").toString()).commit();
        load();
    }
}
