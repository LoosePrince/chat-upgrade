package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceState;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

@Mixin(ChatScreen.class)
public abstract class ChatScreenAudioFloatingWindowMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void chatupgrade$renderAudioFloatingWindow(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatSurfaceController.state().overlay() != ChatSurfaceState.Overlay.NONE) {
            return;
        }
        AudioFloatingWindow.render(graphics, ((ChatScreen) (Object) this).getFont(), graphics.guiWidth(), graphics.guiHeight());
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleAudioFloatingMouseClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatSurfaceController.state().overlay() != ChatSurfaceState.Overlay.NONE) {
            return;
        }
        if (AudioFloatingWindow.mouseClicked(event, ((ChatScreen) (Object) this).width, ((ChatScreen) (Object) this).height)) {
            ChatTextSelectionState.clear();
            cir.setReturnValue(true);
        }
    }

}
