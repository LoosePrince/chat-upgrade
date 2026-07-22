package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsAnchorMixin {
    @Shadow
    @Final
    private EditBox input;

    @Redirect(
            method = "showSuggestions(Z)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screens/Screen;height:I"))
    private int chatupgrade$anchorSuggestionsToInput(Screen ignoredScreen) {
        return input.getY() + CommandSuggestions.LINE_HEIGHT;
    }

    @Redirect(
            method = "extractUsage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screens/Screen;height:I"))
    private int chatupgrade$anchorUsageToInput(Screen ignoredScreen) {
        return input.getY() + CommandSuggestions.USAGE_OFFSET_FROM_BOTTOM;
    }
}