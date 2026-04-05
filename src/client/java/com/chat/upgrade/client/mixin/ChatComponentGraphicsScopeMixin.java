package com.chat.upgrade.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.chat.upgrade.client.ChatGraphicsAccessBridge;
import com.chat.upgrade.client.ChatUpgradeRenderScope;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds {@link GuiGraphicsExtractor} for the duration of chat HUD render passes. Separate from message parsing
 * mixins; mirrors {@code ChatComponent} lifecycle only.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentGraphicsScopeMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private static void chatupgrade$pushScopeFromExtractor(
            CallbackInfo ci,
            @Local(argsOnly = true) GuiGraphicsExtractor guiGraphics
    ) {
        ChatUpgradeRenderScope.push(guiGraphics);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN")
    )
    private static void chatupgrade$popScopeFromExtractor(CallbackInfo ci) {
        ChatUpgradeRenderScope.pop();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD")
    )
    private static void chatupgrade$pushScopeFromAccess(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) ChatComponent.ChatGraphicsAccess graphicsAccess
    ) {
        ChatUpgradeRenderScope.push(ChatGraphicsAccessBridge.unwrap(graphicsAccess));
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("RETURN")
    )
    private static void chatupgrade$popScopeFromAccess(CallbackInfo ci) {
        ChatUpgradeRenderScope.pop();
    }

    @Inject(method = "captureClickableText", at = @At("HEAD"))
    private static void chatupgrade$clearScopeForHitTest(CallbackInfo ci) {
        ChatUpgradeRenderScope.clear();
    }
}
