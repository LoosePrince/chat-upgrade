package com.chat.upgrade.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor for {@code graphics} on chat drawing {@code ChatGraphicsAccess} implementations. */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"
})
public interface ChatDrawingGraphicsAccessor {
    @Accessor("graphics")
    GuiGraphicsExtractor chatupgrade$graphics();
}
