package com.chat.upgrade.client.mixininterface;

/** Cross-version bridge for attachment-tray dragging owned by the chat composer. */
public interface ChatComposerAttachmentDragAccess {
    boolean chatupgrade$updateAttachmentDrag(double mouseX, double mouseY, int button);
}