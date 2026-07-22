package com.chat.upgrade.client.mixin;

import java.nio.file.Path;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.client.ui.chat.input.NativeFileDialogModal;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerModalInputMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockMouseButton(
            long window,
            MouseButtonInfo button,
            int action,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockMouseScroll(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove(JDD)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockMouseMove(
            long window,
            double x,
            double y,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "onDrop(JLjava/util/List;I)V", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$blockFileDrop(
            long window,
            List<Path> paths,
            int rejectedCount,
            CallbackInfo ci) {
        if (NativeFileDialogModal.isActive()) {
            ci.cancel();
        }
    }
}