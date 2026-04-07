package com.chat.upgrade.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentLineHeightAccessor {
    @Invoker("getLineHeight")
    int chatupgrade$invokeGetLineHeight();
}
