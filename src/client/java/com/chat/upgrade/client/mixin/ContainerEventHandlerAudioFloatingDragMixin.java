package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerAudioFloatingDragMixin {
    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleAudioFloatingDrag(
            MouseButtonEvent event,
            double dx,
            double dy,
            CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ChatScreen screen
                && AudioFloatingWindow.mouseDragged(event, dx, dy, screen.width, screen.height)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleAudioFloatingRelease(
            MouseButtonEvent event,
            CallbackInfoReturnable<Boolean> cir) {
        if (AudioFloatingWindow.mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }
}
