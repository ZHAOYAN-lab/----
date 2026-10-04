package com.beaconfinder.app.model;

public final class Beacon {
    public final String code;
    public final String name;
    public final String baseSn;
    public boolean selected;
    public String state = "ステータス待機中";

    public Beacon(String code, String name, String baseSn) {
        this.code = code;
        this.name = name;
        this.baseSn = baseSn;
    }
}
