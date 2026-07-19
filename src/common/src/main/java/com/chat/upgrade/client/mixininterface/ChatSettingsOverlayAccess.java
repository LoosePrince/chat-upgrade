package com.chat.upgrade.client.mixininterface;

/** Cross-version bridge for the settings overlay's pointer capture. */
public interface ChatSettingsOverlayAccess {
    boolean chatupgrade$isSettingsOverlayOpen();

    boolean chatupgrade$updateSettingsDrag(double mouseX, double mouseY, int button);

    boolean chatupgrade$releaseSettingsDrag(int button);
}