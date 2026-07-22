package com.chat.upgrade.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.chat.upgrade.client.mixininterface.ChatCommandSuggestionAreaAccess;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsAnchorMixin {
    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private Screen screen;

    @Redirect(
            method = "showSuggestions(Z)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screens/Screen;height:I"))
    private int chatupgrade$anchorSuggestionsToInput(Screen ignoredScreen) {
        return chatupgrade$suggestionArea().bottom() + CommandSuggestions.LINE_HEIGHT;
    }

    @Redirect(
            method = "extractUsage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screens/Screen;height:I"))
    private int chatupgrade$anchorUsageToInput(Screen ignoredScreen) {
        return chatupgrade$suggestionArea().bottom() + CommandSuggestions.USAGE_OFFSET_FROM_BOTTOM;
    }

    @Redirect(
            method = { "showSuggestions(Z)V", "updateCommandInfo()V" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/EditBox;getScreenX(I)I",
                    ordinal = 0))
    private int chatupgrade$clampSuggestionStart(EditBox currentInput, int characterIndex) {
        return Math.max(currentInput.getScreenX(characterIndex), chatupgrade$suggestionArea().left());
    }

    @Redirect(
            method = { "showSuggestions(Z)V", "updateCommandInfo()V" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/EditBox;getScreenX(I)I",
                    ordinal = 1))
    private int chatupgrade$availableSuggestionLeft(EditBox ignoredInput, int ignoredCharacterIndex) {
        return chatupgrade$suggestionArea().left();
    }

    @Redirect(
            method = { "showSuggestions(Z)V", "updateCommandInfo()V" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/EditBox;getInnerWidth()I"))
    private int chatupgrade$availableSuggestionWidth(EditBox ignoredInput) {
        return chatupgrade$suggestionArea().width();
    }

    @Redirect(
            method = "showSuggestions(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int chatupgrade$capSuggestionWidth(Font font, String text) {
        return Math.min(font.width(text), chatupgrade$suggestionArea().width());
    }

    @Redirect(
            method = "recomputeUsageBoxWidth()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/util/FormattedCharSequence;)I"))
    private int chatupgrade$capUsageWidth(Font font, FormattedCharSequence text) {
        return Math.min(font.width(text), chatupgrade$suggestionArea().width());
    }

    private RichChatBounds chatupgrade$suggestionArea() {
        if (screen instanceof ChatCommandSuggestionAreaAccess access) {
            return access.chatupgrade$commandSuggestionArea();
        }
        int left = input.getScreenX(0);
        return new RichChatBounds(
                left,
                0,
                left + Math.max(1, input.getInnerWidth()),
                Math.max(1, input.getY()));
    }
}