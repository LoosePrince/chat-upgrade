package com.chat.upgrade.client;

import net.minecraft.client.gui.components.ChatComponent;

/**
 * Implemented on {@link ChatComponent} by {@link com.chat.upgrade.client.mixin.ChatComponentMixin} so asynchronous
 * URL fetch completion can refresh trimmed chat lines.
 */
public interface UpgradeChatHudSync {
    void refreshInlineLayoutForUrl(String url);
}
