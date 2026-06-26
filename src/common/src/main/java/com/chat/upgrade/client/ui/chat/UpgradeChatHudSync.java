package com.chat.upgrade.client.ui.chat;
import net.minecraft.client.gui.components.ChatComponent;

/**
 * Implemented on {@link ChatComponent} by {@link com.chat.upgrade.client.mixin.ChatComponentMixin} so asynchronous
 * URL fetch completion can refresh trimmed chat lines.
 */
public interface UpgradeChatHudSync {
    void refreshInlineLayoutForUrl(String url);

    /** Insert phantom preview rows immediately after {@link ImageLoader#getOrLoad(String)} (e.g. manual reveal click). */
    void requestLayoutSyncForUrl(String url);
}
