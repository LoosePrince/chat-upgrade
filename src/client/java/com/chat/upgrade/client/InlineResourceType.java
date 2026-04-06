package com.chat.upgrade.client;

public enum InlineResourceType {
    IMAGE,
    AUDIO;

    public static InlineResourceType fromWire(String value) {
        if (value == null) {
            return IMAGE;
        }
        return "audio".equalsIgnoreCase(value.trim()) ? AUDIO : IMAGE;
    }
}
