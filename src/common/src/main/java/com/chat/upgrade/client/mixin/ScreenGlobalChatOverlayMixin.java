package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ui.chat.notification.MentionNotificationService;
import com.chat.upgrade.client.media.audio.VoiceShortcutService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds passthrough notifications after the active screen has submitted its render state. */
@Mixin(Screen.class)
public abstract class ScreenGlobalChatOverlayMixin {
    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"))
    private void chatupgrade$renderGlobalChatOverlay(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((Object) this instanceof ChatScreen || minecraft == null || minecraft.font == null) {
            return;
        }
        VoiceShortcutService.renderPrompt(graphics, minecraft.font, graphics.guiWidth(), graphics.guiHeight());
        MentionNotificationService.renderPassthrough(
                graphics,
                minecraft.font,
                graphics.guiWidth(),
                graphics.guiHeight());
    }
}