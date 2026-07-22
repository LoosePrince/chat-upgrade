package com.chat.upgrade.client.ui.chat.input;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeConfig;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

public final class ChatInputButton extends Button.Plain {
    public ChatInputButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!ChatUpgradeConfig.isModButtonArrowNavigationEnabled()
                && event instanceof FocusNavigationEvent.ArrowNavigation) {
            return null;
        }
        return super.nextFocusPath(event);
    }
}
