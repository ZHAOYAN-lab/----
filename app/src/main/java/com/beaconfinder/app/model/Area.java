package com.beaconfinder.app.model;

import java.io.Serializable;

public class Area implements Serializable {
    public String id;
    public String warehouseId;
    public String name;
    public long createdAt;

    public Area() {
    }

    public Area(String id, String warehouseId, String name, long createdAt) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.name = name;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
