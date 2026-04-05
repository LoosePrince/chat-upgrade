package com.chat.upgrade.client;

import com.chat.upgrade.client.mixin.ChatDrawingGraphicsAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

public final class ChatGraphicsAccessBridge {
    private ChatGraphicsAccessBridge() {}

    public static @Nullable GuiGraphicsExtractor unwrap(@Nullable net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess access) {
        if (access == null) return null;
        if (access instanceof ChatDrawingGraphicsAccessor a) {
            return a.chatupgrade$graphics();
        }
        return null;
    }
}
