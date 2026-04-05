package com.chat.upgrade.client.mixininterface;

import org.jetbrains.annotations.Nullable;

/** Per-line image URL / continuation phantom (via {@link com.chat.upgrade.client.mixin.GuiMessageLineMixin}). */
public interface ImageAttachable {
    @Nullable String chatupgrade$getImageUrl();

    boolean chatupgrade$isImageContinuation();
}
