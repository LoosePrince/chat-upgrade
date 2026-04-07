package com.chat.upgrade.client.mixin;

import java.util.List;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatComponentScrollAccessors {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> chatupgrade$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int chatupgrade$getChatScrollbarPos();
}
