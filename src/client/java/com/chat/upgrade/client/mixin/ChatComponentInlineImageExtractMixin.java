package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ChatUpgradeInlineImageInteraction;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentInlineImageExtractMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD")
    )
    private void chatupgrade$clearInlineImageHits(CallbackInfo ci) {
        ChatUpgradeInlineImageInteraction.clearForExtractPass();
    }
}
