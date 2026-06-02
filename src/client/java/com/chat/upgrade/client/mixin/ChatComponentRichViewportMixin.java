package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ui.chat.ChatGraphicsAccessBridge;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayoutEngine;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaRenderer;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMessageLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatRenderNode;
import com.chat.upgrade.client.ui.chat.viewport.RichChatRenderNodeKind;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportMetrics;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatComponent.class)
public abstract class ChatComponentRichViewportMixin {
    @Unique
    private static final RichChatLayoutEngine chatupgrade$layoutEngine = new RichChatLayoutEngine();

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    protected abstract int getWidth();

    @Shadow
    protected abstract int getHeight();

    @Shadow
    protected abstract double getScale();

    @WrapOperation(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"
            )
    )
    private void chatupgrade$renderRichViewport(
            ChatComponent instance,
            ChatComponent.ChatGraphicsAccess graphics,
            int screenHeight,
            int ticks,
            ChatComponent.DisplayMode displayMode,
            Operation<Void> original) {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode() || displayMode.showRestrictedPrompt) {
            RichChatInteractionRouter.clear();
            original.call(instance, graphics, screenHeight, ticks, displayMode);
            return;
        }

        Font font = minecraft.font;
        RichChatViewportMetrics metrics = chatupgrade$metrics(screenHeight, displayMode);
        RichChatLayout layout = chatupgrade$layoutEngine.layoutFromStore(font, metrics);
        if (layout.totalHeight() <= 0) {
            RichChatInteractionRouter.clear();
            original.call(instance, graphics, screenHeight, ticks, displayMode);
            return;
        }

        GuiGraphicsExtractor extractor = ChatGraphicsAccessBridge.unwrap(graphics);
        RichChatViewportState state = RichChatViewport.state();
        state.updateContentBounds(layout.totalHeight(), metrics.visibleHeight());
        int contentToLocalY = metrics.chatBottom() - (layout.totalHeight() - state.scrollPx());

        boolean ownsClip = false;
        if (extractor != null && !ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            ChatUpgradeChatRenderState.beginRenderPass(
                    extractor,
                    screenHeight,
                    metrics.scale(),
                    metrics.visibleHeight(),
                    metrics.maxWidth());
            ownsClip = true;
        }

        graphics.updatePose(pose -> {
            pose.scale((float) metrics.scale(), (float) metrics.scale());
            pose.translate(4.0F, 0.0F);
        });
        try {
            if (extractor != null && displayMode.foreground) {
                RichChatInteractionRouter.setActiveLayout(
                        layout,
                        state,
                        new Matrix3x2f(extractor.pose()),
                        contentToLocalY);
            }
            chatupgrade$paintMessages(graphics, extractor, font, metrics, layout, state, contentToLocalY, ticks,
                    displayMode.foreground);
            if (displayMode.foreground) {
                chatupgrade$paintScrollBar(graphics, metrics, layout, state);
            }
        } finally {
            if (ownsClip && extractor != null) {
                ChatUpgradeChatRenderState.endRenderPass(extractor);
            }
        }
    }

    @Unique
    private RichChatViewportMetrics chatupgrade$metrics(int screenHeight, ChatComponent.DisplayMode displayMode) {
        float textOpacity = minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
        float backgroundOpacity = minecraft.options.textBackgroundOpacity().get().floatValue();
        double lineSpacing = minecraft.options.chatLineSpacing().get();
        return RichChatViewportMetrics.fromVanilla(
                screenHeight,
                getScale(),
                getWidth(),
                getHeight(),
                lineSpacing,
                textOpacity,
                backgroundOpacity,
                displayMode.foreground);
    }

    @Unique
    private void chatupgrade$paintMessages(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatLayout layout,
            RichChatViewportState state,
            int contentToLocalY,
            int ticks,
            boolean foreground) {
        int visibleTop = state.visibleTop();
        int visibleBottom = state.visibleBottom();
        for (RichChatMessageLayout messageLayout : layout.messages()) {
            if (!messageLayout.visibleIn(visibleTop, visibleBottom)) {
                continue;
            }
            float alpha = chatupgrade$alpha(messageLayout, ticks, foreground);
            if (alpha <= 1.0e-5F) {
                continue;
            }
            RichChatBounds localMessage = messageLayout.bounds().translateY(contentToLocalY);
            graphics.fill(
                    metrics.backgroundLeft(),
                    localMessage.top(),
                    metrics.backgroundRight(),
                    localMessage.bottom(),
                    ARGB.black(alpha * metrics.backgroundOpacity()));
            for (RichChatRenderNode node : messageLayout.nodes()) {
                if (!node.bounds().intersectsVerticalRange(visibleTop, visibleBottom)) {
                    continue;
                }
                chatupgrade$paintNode(graphics, extractor, font, metrics, node, contentToLocalY, alpha);
            }
        }
    }

    @Unique
    private void chatupgrade$paintNode(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            int contentToLocalY,
            float alpha) {
        RichChatBounds localBounds = node.bounds().translateY(contentToLocalY);
        if (node.kind() == RichChatRenderNodeKind.TEXT || node.kind() == RichChatRenderNodeKind.SYSTEM) {
            if (node.text() != null) {
                int textY = localBounds.bottom() - metrics.entryBottomToMessageY();
                graphics.handleMessage(textY, alpha * metrics.textOpacity(), node.text());
            }
            return;
        }
        if (extractor != null) {
            RichChatMediaRenderer.paintNode(extractor, font, metrics, node, contentToLocalY, alpha);
        }
    }

    @Unique
    private void chatupgrade$paintScrollBar(
            ChatComponent.ChatGraphicsAccess graphics,
            RichChatViewportMetrics metrics,
            RichChatLayout layout,
            RichChatViewportState state) {
        if (layout.totalHeight() <= metrics.visibleHeight() || metrics.visibleHeight() <= 0) {
            return;
        }
        int barHeight = Math.max(2, metrics.visibleHeight() * metrics.visibleHeight() / layout.totalHeight());
        int scrollOffset = state.scrollPx() * metrics.visibleHeight() / layout.totalHeight();
        int bottom = metrics.chatBottom() - scrollOffset;
        int alpha = state.scrollPx() > 0 ? 170 : 96;
        int color = newMessageSinceScroll ? 13382451 : 3355562;
        int x = metrics.scrollbarX();
        graphics.fill(x, bottom, x + 2, bottom - barHeight, ARGB.color(alpha, color));
        graphics.fill(x + 2, bottom, x + 1, bottom - barHeight, ARGB.color(alpha, 13421772));
    }

    @Unique
    private float chatupgrade$alpha(RichChatMessageLayout layout, int ticks, boolean foreground) {
        if (foreground) {
            return 1.0F;
        }
        int tickDelta = ticks - layout.message().addedTime();
        double t = tickDelta / 200.0D;
        t = 1.0D - t;
        t *= 10.0D;
        t = Mth.clamp(t, 0.0D, 1.0D);
        t *= t;
        return (float) t;
    }
}