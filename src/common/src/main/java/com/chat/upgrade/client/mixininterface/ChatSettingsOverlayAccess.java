package com.chat.upgrade.client.mixininterface;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;

/** Cross-version bridge for the settings overlay's pointer and text input capture. */
public interface ChatSettingsOverlayAccess {
    boolean chatupgrade$isSettingsOverlayOpen();

    boolean chatupgrade$updateSettingsDrag(MouseButtonEvent event, double dx, double dy);

    boolean chatupgrade$releaseSettingsDrag(MouseButtonEvent event);

    boolean chatupgrade$settingsCharTyped(CharacterEvent event);

    boolean chatupgrade$settingsPreeditUpdated(PreeditEvent event);
}