package com.chat.upgrade.client.media.model;

public enum InlineResourceType {
    IMAGE,
    AUDIO,
    VIDEO;

    public String toWire() {
        return switch (this) {
            case IMAGE -> "image";
            case AUDIO -> "audio";
            case VIDEO -> "video";
        };
    }

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
