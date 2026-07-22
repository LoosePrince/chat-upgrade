package com.chat.upgrade.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.ChatUpgradeInlineImageInteraction;
import com.chat.upgrade.client.ui.chat.InlineEmojiHudPaint;
import com.chat.upgrade.client.ui.chat.UpgradePhantomHudLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaHoverState;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hook {@code handleMessage} so inline images draw between row background and text. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatComponentInnerMixin {

    @WrapOperation(
            method = "accept",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"
            )
    )
    private boolean chatupgrade$drawImageBeforeLineText(
            ChatComponent.ChatGraphicsAccess graphics,
            int y,
            float opacity,
            FormattedCharSequence text,
            Operation<Boolean> original,
            @Local(argsOnly = true, ordinal = 0) GuiMessage.Line line
    ) {
        if (!ChatUpgradeChatPipelineGate.shouldRenderLineEnhancements(line)) {
            return original.call(graphics, y, opacity, text);
        }
        float smoothOffset = ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()
                ? ChatUpgradeChatRenderState.smoothOffsetPx()
                : 0.0F;
        int lineHeight = chatupgrade$lineHeight();
        graphics.updatePose(pose -> pose.translate(0.0F, smoothOffset));
        try {
            if (graphics instanceof ChatUpgradeDrawingFocusedAccessor focused) {
                var localMouse = focused.chatupgrade$localMousePos();
                RichChatMediaHoverState.update(localMouse.x, localMouse.y);
            } else {
                RichChatMediaHoverState.clear();
            }
            UpgradePhantomHudLayout.dispatchLinePaint(line, y, opacity);
            InlineEmojiHudPaint.paintLineEmoji(line, y, opacity, lineHeight);
            ChatUpgradeInlineImageInteraction.afterChatLinePaint(graphics, line, y, opacity, lineHeight);
            return original.call(graphics, y, opacity, text);
        } finally {
            graphics.updatePose(pose -> pose.translate(0.0F, -smoothOffset));
        }
    }

    private int chatupgrade$lineHeight() {
        ChatComponent outer = ((ChatComponentInnerOuterAccessor) (Object) this).chatupgrade$getOuterChatComponent();
        return Math.max(1, ((ChatComponentLineHeightAccessor) outer).chatupgrade$invokeGetLineHeight());
    }
}
