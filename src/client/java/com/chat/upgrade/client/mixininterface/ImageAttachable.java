package com.chat.upgrade.client.mixininterface;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;

/** Per-line image URL / continuation phantom (via {@link com.chat.upgrade.client.mixin.GuiMessageLineMixin}). */
public interface ImageAttachable {
    @Nullable String chatupgrade$getImageUrl();
    @Nullable String chatupgrade$getResourceName();

    boolean chatupgrade$isImageContinuation();

    InlineResourceType chatupgrade$getResourceType();

    List<InlineEmojiSlot> chatupgrade$getInlineEmojiSlots();
}
