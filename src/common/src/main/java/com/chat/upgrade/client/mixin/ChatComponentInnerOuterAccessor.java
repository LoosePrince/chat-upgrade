package com.chat.upgrade.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public interface ChatComponentInnerOuterAccessor {
    @Accessor("this$0")
    ChatComponent chatupgrade$getOuterChatComponent();
}
