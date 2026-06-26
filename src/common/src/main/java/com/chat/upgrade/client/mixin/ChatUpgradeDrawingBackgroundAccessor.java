package com.chat.upgrade.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public interface ChatUpgradeDrawingBackgroundAccessor {
    @Accessor("graphics")
    GuiGraphicsExtractor chatupgrade$graphics();
}
