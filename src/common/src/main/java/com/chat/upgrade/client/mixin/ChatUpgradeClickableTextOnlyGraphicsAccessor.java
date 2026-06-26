package com.chat.upgrade.client.mixin;

import net.minecraft.client.gui.ActiveTextCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$ClickableTextOnlyGraphicsAccess")
public interface ChatUpgradeClickableTextOnlyGraphicsAccessor {
    @Accessor("output")
    ActiveTextCollector chatupgrade$output();
}
