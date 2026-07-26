package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.client.ui.chat.input.NativeFileDialogModal;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.audio.VoiceShortcutService;
import com.chat.upgrade.client.mixininterface.ChatSettingsOverlayAccess;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerModalInputMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleGlobalVoiceShortcut(
            long window,
            int action,
            KeyEvent event,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive() || chatupgrade$settingsCapturingInput()) {
            return;
        }
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS
                && VoiceShortcutService.handleKeyPressed(event.key(), event.hasShiftDown())) {
            ci.cancel();
            return;
        }
        if (action == org.lwjgl.glfw.GLFW.GLFW_RELEASE
                && VoiceShortcutService.handleKeyReleased(event.key())) {
            ci.cancel();
        }
    }

    private boolean chatupgrade$settingsCapturingInput() {
        return MinecraftGuiBridge.currentScreen(Minecraft.getInstance()) instanceof ChatScreen screen
                && screen instanceof ChatSettingsOverlayAccess settings
                && settings.chatupgrade$isSettingsOverlayOpen();
    }

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockKeyPress(
            long window,
            int action,
            KeyEvent event,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockCharacterInput(
            long window,
            CharacterEvent event,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "preeditCallback(JLnet/minecraft/client/input/PreeditEvent;)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockPreeditInput(
            long window,
            PreeditEvent event,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }
}