package com.chat.upgrade.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerActiveButtonAccessor {
    @Accessor("activeButton")
    @Nullable
    MouseButtonInfo chatupgrade$getActiveButton();
}
