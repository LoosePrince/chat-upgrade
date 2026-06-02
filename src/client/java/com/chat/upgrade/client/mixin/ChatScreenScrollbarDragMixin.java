package com.chat.upgrade.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenScrollbarDragMixin {
    @Unique
    private boolean chatupgrade$draggingScrollbar;

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$beginScrollbarDrag(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChatUpgradeChatPipelineGate.shouldUseRichViewportInteractions()) {
            chatupgrade$beginViewportScrollbarDrag(event, cir);
            return;
        }
        if (!ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            chatupgrade$draggingScrollbar = false;
            return;
        }
        if (event.button() != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentScrollAccessors accessors)) {
            return;
        }
        if (!chat.isChatFocused()) {
            return;
        }

        int total = accessors.chatupgrade$getTrimmedMessages().size();
        if (total <= 0) {
            return;
        }
        int perPage = chat.getLinesPerPage();
        int maxScroll = Math.max(0, total - perPage);
        if (maxScroll <= 0) {
            return;
        }

        if (!chatupgrade$isOverScrollbar(mc, chat, event.x(), event.y())) {
            return;
        }

        chatupgrade$draggingScrollbar = true;
        chatupgrade$applyScrollbarDrag(mc, chat, total, perPage, event.y());
        cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void chatupgrade$dragScrollbarOnRender(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float a,
            CallbackInfo ci) {
        if (ChatUpgradeChatPipelineGate.shouldUseRichViewportInteractions()) {
            chatupgrade$dragViewportScrollbarOnRender(mouseY);
            return;
        }
        if (!ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            chatupgrade$draggingScrollbar = false;
            return;
        }
        if (!chatupgrade$draggingScrollbar) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        MouseButtonInfo activeButton = ((MouseHandlerActiveButtonAccessor) mc.mouseHandler).chatupgrade$getActiveButton();
        if (activeButton == null || activeButton.button() != 0) {
            chatupgrade$draggingScrollbar = false;
            return;
        }
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentScrollAccessors accessors)) {
            return;
        }
        int total = accessors.chatupgrade$getTrimmedMessages().size();
        int perPage = chat.getLinesPerPage();
        if (total <= 0 || perPage <= 0) {
            return;
        }
        chatupgrade$applyScrollbarDrag(mc, chat, total, perPage, mouseY);
    }

    @Unique
    private void chatupgrade$beginViewportScrollbarDrag(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        chatupgrade$draggingScrollbar = false;
        if (event.button() != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ChatComponent chat = mc.gui.getChat();
        if (!chat.isChatFocused()) {
            return;
        }
        RichChatViewportState state = RichChatViewport.state();
        if (!state.canScroll()) {
            return;
        }
        if (!chatupgrade$isOverScrollbar(mc, chat, event.x(), event.y())) {
            return;
        }
        chatupgrade$draggingScrollbar = true;
        chatupgrade$applyViewportScrollbarDrag(mc, state, event.y());
        cir.setReturnValue(true);
    }

    @Unique
    private void chatupgrade$dragViewportScrollbarOnRender(int mouseY) {
        if (!chatupgrade$draggingScrollbar) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            chatupgrade$draggingScrollbar = false;
            return;
        }
        MouseButtonInfo activeButton = ((MouseHandlerActiveButtonAccessor) mc.mouseHandler).chatupgrade$getActiveButton();
        if (activeButton == null || activeButton.button() != 0) {
            chatupgrade$draggingScrollbar = false;
            return;
        }
        RichChatViewportState state = RichChatViewport.state();
        if (!state.canScroll()) {
            chatupgrade$draggingScrollbar = false;
            return;
        }
        chatupgrade$applyViewportScrollbarDrag(mc, state, mouseY);
    }

    @Unique
    private static void chatupgrade$applyViewportScrollbarDrag(
            Minecraft mc,
            RichChatViewportState state,
            double mouseY) {
        double scale = mc.options.chatScale().get();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int chatBottom = Mth.floor((screenHeight - 40.0D) / scale);
        int visibleHeight = Math.max(1, state.visibleHeight());
        double localY = mouseY / scale;
        double top = chatBottom - visibleHeight;
        double t = (localY - top) / Math.max(1.0D, visibleHeight);
        t = Mth.clamp(t, 0.0D, 1.0D);
        int desired = Mth.floor((1.0D - t) * state.maxScrollPx());
        state.setScrollPx(desired);
        ChatUpgradeChatRenderState.cancelWheelOverscroll();
    }

    @Unique
    private static boolean chatupgrade$isOverScrollbar(Minecraft mc, ChatComponent chat, double mouseX, double mouseY) {
        double scale = mc.options.chatScale().get();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int chatBottom = Mth.floor((screenHeight - 40.0D) / scale);
        int chatTop = chatBottom - chatupgrade$chatHeightFocused(mc);

        int maxWidth = Mth.ceil(chatupgrade$chatWidth(mc) / scale);
        int scrollBarStartX = maxWidth + 3;
        int scrollBarEndX = scrollBarStartX + 8;

        int screenLeft = Mth.floor((scrollBarStartX + 4.0D) * scale);
        int screenRight = Mth.ceil((scrollBarEndX + 4.0D) * scale);
        int screenTop = Mth.floor(chatTop * scale);
        int screenBottom = Mth.ceil(chatBottom * scale);
        return mouseX >= screenLeft && mouseX <= screenRight && mouseY >= screenTop && mouseY <= screenBottom;
    }

    @Unique
    private static void chatupgrade$applyScrollbarDrag(
            Minecraft mc,
            ChatComponent chat,
            int totalLines,
            int perPage,
            double mouseY) {
        double scale = mc.options.chatScale().get();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int chatBottom = Mth.floor((screenHeight - 40.0D) / scale);

        int messageHeight = 9;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int) (messageHeight * (chatLineSpacing + 1.0));

        int visibleCount = Math.min(totalLines, perPage);
        int chatHeight = visibleCount * entryHeight;
        int maxScroll = Math.max(0, totalLines - perPage);

        double localY = mouseY / scale;
        double top = chatBottom - chatHeight;
        double t = (localY - top) / Math.max(1.0D, chatHeight);
        t = Mth.clamp(t, 0.0D, 1.0D);

        double desiredFloat = (1.0D - t) * maxScroll;
        int desired = Mth.floor(desiredFloat);
        double residual = desiredFloat - desired;
        int current = ((ChatComponentScrollAccessors) chat).chatupgrade$getChatScrollbarPos();
        int delta = desired - current;
        if (delta != 0) {
            chat.scrollChat(delta);
        }
        int lineHeight = ((ChatComponentLineHeightAccessor) chat).chatupgrade$invokeGetLineHeight();
        ChatUpgradeChatRenderState.setScrollResidualLines(residual, lineHeight);
    }

    @Unique
    private static int chatupgrade$chatWidth(Minecraft mc) {
        double chatWidth = mc.options.chatWidth().get();
        return (int) Math.floor(chatWidth * 280.0 + 40.0);
    }

    @Unique
    private static int chatupgrade$chatHeightFocused(Minecraft mc) {
        double chatHeight = mc.options.chatHeightFocused().get();
        return (int) Math.floor(chatHeight * 160.0 + 20.0);
    }
}
