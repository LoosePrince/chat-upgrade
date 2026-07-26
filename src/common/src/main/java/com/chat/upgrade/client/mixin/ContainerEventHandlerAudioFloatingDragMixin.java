package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.mixininterface.ChatComposerAttachmentDragAccess;
import com.chat.upgrade.client.mixininterface.ChatSettingsOverlayAccess;
import com.chat.upgrade.client.mixininterface.VoiceRecordingInputAccess;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureArena;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerAudioFloatingDragMixin {
    @Inject(method = "charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$captureSettingsCharacter(
            CharacterEvent event,
            CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ChatScreen
                && self instanceof ChatSettingsOverlayAccess settingsOverlay
                && settingsOverlay.chatupgrade$isSettingsOverlayOpen()
                && settingsOverlay.chatupgrade$settingsCharTyped(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "preeditUpdated(Lnet/minecraft/client/input/PreeditEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$captureSettingsPreedit(
            PreeditEvent event,
            CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ChatScreen
                && self instanceof ChatSettingsOverlayAccess settingsOverlay
                && settingsOverlay.chatupgrade$isSettingsOverlayOpen()
                && settingsOverlay.chatupgrade$settingsPreeditUpdated(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleAudioFloatingDrag(
            MouseButtonEvent event,
            double dx,
            double dy,
            CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ChatScreen
                && self instanceof ChatSettingsOverlayAccess settingsOverlay
                && settingsOverlay.chatupgrade$isSettingsOverlayOpen()
                && settingsOverlay.chatupgrade$updateSettingsDrag(event, dx, dy)) {
            cir.setReturnValue(true);
            return;
        }
        if (self instanceof ChatScreen screen
                && AudioFloatingWindow.mouseDragged(event, dx, dy, screen.width, screen.height)) {
            cir.setReturnValue(true);
            return;
        }
        if (self instanceof ChatScreen
                && ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatSurfaceController.pointerDragged(event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
        }
        if (self instanceof ChatScreen
                && self instanceof ChatComposerAttachmentDragAccess attachmentDrag
                && attachmentDrag.chatupgrade$updateAttachmentDrag(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (self instanceof ChatScreen
                && ChatUpgradeChatPipelineGate.isTakeoverMode()
                && event.button() == 0
                && RichChatInteractionRouter.updatePointerAtScreen(
                        (int) Math.round(event.x()),
                        (int) Math.round(event.y()))) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleAudioFloatingRelease(
            MouseButtonEvent event,
            CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ChatScreen
                && self instanceof VoiceRecordingInputAccess voiceInput
                && voiceInput.chatupgrade$releaseVoiceMouse(event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (self instanceof ChatScreen
                && self instanceof ChatSettingsOverlayAccess settingsOverlay
                && settingsOverlay.chatupgrade$isSettingsOverlayOpen()
                && settingsOverlay.chatupgrade$releaseSettingsDrag(event)) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0 && ChatGestureArena.consumePendingRelease()) {
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatSurfaceController.pointerReleased(event.button())) {
            RichChatInteractionRouter.cancelPointerCapture();
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && event.button() == 0
                && RichChatInteractionRouter.finishPointerAtScreen()) {
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && event.button() == 0
                && ChatGestureArena.cancelOnRelease(ChatGestureArena.Owner.ATTACHMENT_TRAY)) {
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && event.button() == 0
                && ChatGestureArena.cancelOnRelease(ChatGestureArena.Owner.SCROLLBAR)) {
            cir.setReturnValue(true);
            return;
        }
        if (AudioFloatingWindow.mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }
}
