package com.chat.upgrade.client.ui.settings;

public enum SettingsCategory {
    APPEARANCE("chatupgrade.settings.category.appearance"),
    CHAT_BEHAVIOR("chatupgrade.settings.category.chat_behavior"),
    MEDIA("chatupgrade.settings.category.media"),
    UPLOAD_COMPATIBILITY("chatupgrade.settings.category.upload_compatibility");

    private final String labelKey;

    SettingsCategory(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}