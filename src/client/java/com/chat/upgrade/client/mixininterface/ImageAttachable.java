package com.chat.upgrade.client.mixininterface;

import com.chat.upgrade.client.InlineResourceType;
import org.jetbrains.annotations.Nullable;

/** Per-line image URL / continuation phantom (via {@link com.chat.upgrade.client.mixin.GuiMessageLineMixin}). */
public interface ImageAttachable {
    @Nullable String chatupgrade$getImageUrl();
    @Nullable String chatupgrade$getResourceName();

    boolean chatupgrade$isImageContinuation();

    InlineResourceType chatupgrade$getResourceType();
}
