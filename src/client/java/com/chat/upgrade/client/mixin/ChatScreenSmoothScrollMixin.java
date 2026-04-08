package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatScreen.class)
public abstract class ChatScreenSmoothScrollMixin {
    @WrapOperation(
            method = "mouseScrolled(DDDD)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;scrollChat(I)V"
            )
    )
    private void chatupgrade$smoothWheelScroll(
            ChatComponent chatComponent,
            int ignoredIntScroll,
            Operation<Void> original,
            @Local(name = "scrollY") double scaledScrollY
    ) {
        if (!ChatUpgradeConfig.isSmoothScrollEnabled()) {
            ChatUpgradeChatRenderState.cancelWheelOverscroll();
            original.call(chatComponent, ignoredIntScroll);
            return;
        }
        int lineHeight = ((ChatComponentLineHeightAccessor) chatComponent).chatupgrade$invokeGetLineHeight();
        ChatComponentScrollAccessors accessors = (ChatComponentScrollAccessors) chatComponent;
        int total = accessors.chatupgrade$getTrimmedMessages().size();
        int maxScroll = Math.max(0, total - chatComponent.getLinesPerPage());
        int before = accessors.chatupgrade$getChatScrollbarPos();

        // At bounds and trying to scroll further out: cancel residual/animation immediately.
        if ((before <= 0 && scaledScrollY < 0.0D) || (before >= maxScroll && scaledScrollY > 0.0D)) {
            ChatUpgradeChatRenderState.cancelWheelOverscroll();
            return;
        }

        int lines = ChatUpgradeChatRenderState.consumeWheelScrollLines(scaledScrollY, lineHeight);
        if (lines != 0) {
            original.call(chatComponent, lines);
            int after = accessors.chatupgrade$getChatScrollbarPos();
            if (after == before) {
                // Requested scroll got clamped completely: drop overscroll state.
                ChatUpgradeChatRenderState.cancelWheelOverscroll();
            }
        }
    }
}
