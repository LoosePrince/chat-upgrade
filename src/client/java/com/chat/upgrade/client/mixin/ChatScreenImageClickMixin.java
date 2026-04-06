package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ChatUpgradeInlineImageInteraction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatScreen.class)
public abstract class ChatScreenImageClickMixin {

    @WrapOperation(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;result()Lnet/minecraft/network/chat/Style;"
            )
    )
    private Style chatupgrade$styleFinderOrInlineImage(
            ActiveTextCollector.ClickableStyleFinder instance,
            Operation<Style> original,
            @Local(argsOnly = true) MouseButtonEvent event
    ) {
        Style base = original.call(instance);
        if (base != null) {
            return base;
        }
        return ChatUpgradeInlineImageInteraction.styleForScreenClick((int) event.x(), (int) event.y());
    }
}
