package com.beaconfinder.app.model;

public final class BaseStation {
    public final String sn;
    public final String name;

    public BaseStation(String sn, String name) {
        this.sn = sn;
        this.name = name;
    }

    @Override public String toString() {
        return name + "  ·  " + sn;
    }
}
