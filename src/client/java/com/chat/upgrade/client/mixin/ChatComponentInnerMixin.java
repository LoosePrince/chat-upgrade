package com.chat.upgrade.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.chat.upgrade.client.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ChatUpgradeInlineImageInteraction;
import com.chat.upgrade.client.UpgradePhantomHudLayout;
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
        float smoothOffset = ChatUpgradeChatRenderState.smoothOffsetPx();
        graphics.updatePose(pose -> pose.translate(0.0F, smoothOffset));
        try {
            UpgradePhantomHudLayout.dispatchLinePaint(line, y, opacity);
            ChatUpgradeInlineImageInteraction.afterChatLinePaint(graphics, line, y, opacity);
            return original.call(graphics, y, opacity, text);
        } finally {
            graphics.updatePose(pose -> pose.translate(0.0F, -smoothOffset));
        }
    }
}
