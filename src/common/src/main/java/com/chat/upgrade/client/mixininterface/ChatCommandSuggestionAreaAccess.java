package com.chat.upgrade.client.mixininterface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

/** Exposes the horizontal and vertical region available to command suggestions. */
public interface ChatCommandSuggestionAreaAccess {
    RichChatBounds chatupgrade$commandSuggestionArea();
}