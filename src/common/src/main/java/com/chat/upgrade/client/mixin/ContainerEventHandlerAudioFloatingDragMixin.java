package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;

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
                && ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatSurfaceController.pointerDragged(event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
        }
        if (self instanceof ChatScreen screen
                && AudioFloatingWindow.mouseDragged(event, dx, dy, screen.width, screen.height)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleAudioFloatingRelease(
            MouseButtonEvent event,
            CallbackInfoReturnable<Boolean> cir) {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatSurfaceController.pointerReleased(event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (AudioFloatingWindow.mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }
}
