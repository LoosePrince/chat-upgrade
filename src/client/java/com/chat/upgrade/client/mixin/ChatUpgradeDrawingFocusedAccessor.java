package com.chat.upgrade.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public interface ChatUpgradeDrawingFocusedAccessor {
    @Accessor("graphics")
    GuiGraphicsExtractor chatupgrade$graphics();

    @Accessor("font")
    Font chatupgrade$font();

    @Accessor("localMousePos")
    Vector2f chatupgrade$localMousePos();

    @Accessor("globalMouseX")
    int chatupgrade$globalMouseX();

    @Accessor("globalMouseY")
    int chatupgrade$globalMouseY();
}
