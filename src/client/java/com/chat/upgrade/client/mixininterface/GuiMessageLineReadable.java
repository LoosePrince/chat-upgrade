package com.chat.upgrade.client.mixininterface;

import net.minecraft.util.FormattedCharSequence;

/** {@link net.minecraft.client.multiplayer.chat.GuiMessage.Line} content (via {@link com.chat.upgrade.client.mixin.GuiMessageLineMixin}). */
public interface GuiMessageLineReadable {
    FormattedCharSequence chatupgrade$content();

    boolean chatupgrade$endOfEntry();
}
