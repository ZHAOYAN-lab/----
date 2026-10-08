package com.beaconfinder.app.model;

import java.io.Serializable;

public class Warehouse implements Serializable {
    public String id;
    public String name;
    public long createdAt;

    public Warehouse() {
    }

    public Warehouse(String id, String name, long createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
