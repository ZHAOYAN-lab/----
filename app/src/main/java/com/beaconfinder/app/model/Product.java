package com.beaconfinder.app.model;

import java.io.Serializable;

public class Product implements Serializable {
    public String id;
    public String name;
    public String sku;
    public String warehouseId;
    public String areaId;
    public String shelf = ""; // Shelf name is scoped to the warehouse and area.
    public String beaconCode; // 10-digit beacon code, null or empty if not bound
    public String memo;
    public long updatedAt;

    public Product() {
    }

    public Product(String id, String name, String sku, String warehouseId, String areaId, String beaconCode, String memo, long updatedAt) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.warehouseId = warehouseId;
        this.areaId = areaId;
        this.beaconCode = beaconCode;
        this.memo = memo;
        this.updatedAt = updatedAt;
    }

    public boolean isBound() {
        return beaconCode != null && !beaconCode.trim().isEmpty();
    }
}
