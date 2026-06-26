package com.chat.upgrade.client.mixininterface;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;

/** Per-line rich attachment / continuation phantom (via {@link com.chat.upgrade.client.mixin.GuiMessageLineMixin}). */
public interface ImageAttachable {
    @Nullable RichAttachment chatupgrade$getAttachment();

    @Nullable String chatupgrade$getImageUrl();
    @Nullable String chatupgrade$getResourceName();

    boolean chatupgrade$isImageContinuation();
    boolean chatupgrade$isImagePhantomTop();

    InlineResourceType chatupgrade$getResourceType();

    List<InlineEmojiSlot> chatupgrade$getInlineEmojiSlots();
}
