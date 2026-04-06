package com.chat.upgrade.client;

public enum InlineResourceType {
    IMAGE,
    AUDIO,
    VIDEO;

    public static InlineResourceType fromWire(String value) {
        if (value == null) {
            return IMAGE;
        }
        String v = value.trim();
        if ("audio".equalsIgnoreCase(v)) {
            return AUDIO;
        }
        if ("video".equalsIgnoreCase(v)) {
            return VIDEO;
        }
        return IMAGE;
    }
}
