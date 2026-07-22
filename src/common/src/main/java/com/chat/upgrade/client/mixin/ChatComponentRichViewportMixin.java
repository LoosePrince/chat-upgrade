package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ui.chat.ChatGraphicsAccessBridge;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.scene.ChatScene;
import com.chat.upgrade.client.ui.chat.scene.ChatSceneRenderer;
import com.chat.upgrade.client.ui.chat.surface.ChatPresentationMode;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceFrame;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaHoverState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayoutEngine;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportMetrics;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportState;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
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
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatMediaHoverState.clear();
            RichChatInteractionRouter.cancelPointerCapture();
            RichChatInteractionRouter.clearActiveLayout();
            original.call(instance, graphics, screenHeight, ticks, displayMode);
            return;
        }

        GuiGraphicsExtractor extractor = ChatGraphicsAccessBridge.unwrap(graphics);
        int screenWidth = extractor == null
                ? minecraft.getWindow().getGuiScaledWidth()
                : extractor.guiWidth();
        ChatSurfaceFrame surfaceFrame = ChatSurfaceController.synchronize(
                screenWidth,
                screenHeight,
                displayMode.foreground,
                displayMode.showRestrictedPrompt);
        Font font = minecraft.font;
        RichChatViewportMetrics metrics = chatupgrade$metrics(screenHeight, displayMode, surfaceFrame);
        RichChatLayout layout = chatupgrade$layoutEngine.layoutFromStore(
                font,
                metrics,
                surfaceFrame.appearance(),
                surfaceFrame.presentationMode());
        ChatScene scene = new ChatScene(surfaceFrame, metrics, layout);
        boolean paintsTimeline = !surfaceFrame.restricted() && layout.totalHeight() > 0;

        if (extractor != null) {
            ChatSceneRenderer.paintSurface(extractor, font, scene);
        }

        RichChatViewportState state = RichChatViewport.state();
        state.updateContentBounds(paintsTimeline ? layout.totalHeight() : 0, metrics.visibleHeight());
        state.tickSmoothOffset();
        int contentToLocalY = metrics.chatBottom() - (layout.totalHeight() - state.visualScrollPx());

        boolean ownsClip = false;
        if (extractor != null) {
            chatupgrade$beginClip(extractor, screenHeight, metrics, surfaceFrame);
            ownsClip = true;
        }

        int surfaceTranslateX = surfaceFrame.presentationMode() == ChatPresentationMode.OPEN_PANEL
                ? surfaceFrame.messageViewportBounds().left() + 4
                : 4;
        graphics.updatePose(pose -> {
            pose.scale((float) metrics.scale(), (float) metrics.scale());
            pose.translate(surfaceTranslateX, 0.0F);
        });
        try {
            Matrix3x2f activePose = chatupgrade$activePose(graphics, extractor);
            RichChatBounds viewportBounds = chatupgrade$viewportBounds(metrics);
            if (displayMode.foreground && paintsTimeline && activePose != null) {
                RichChatInteractionRouter.setActiveLayout(
                        layout,
                        state,
                        activePose,
                        contentToLocalY,
                        viewportBounds,
                        surfaceFrame.appearance(),
                        surfaceFrame.presentationMode() == ChatPresentationMode.OPEN_PANEL
                                && surfaceFrame.appearance().vanillaStyleInput());
            } else {
                RichChatInteractionRouter.clearActiveLayout();
            }
            if (displayMode.foreground && graphics instanceof ChatUpgradeDrawingFocusedAccessor focused) {
                Vector2f localMouse = focused.chatupgrade$localMousePos();
                RichChatMediaHoverState.update(localMouse.x, localMouse.y);
            } else {
                RichChatMediaHoverState.clear();
            }
            if (paintsTimeline) {
                Runnable paintTimeline = () -> {
                    ChatSceneRenderer.paintTimeline(
                            graphics,
                            extractor,
                            font,
                            scene,
                            state,
                            contentToLocalY,
                            ticks,
                            displayMode.foreground);
                    if (displayMode.foreground) {
                        ChatSceneRenderer.paintScrollbar(graphics, scene, state, newMessageSinceScroll);
                    }
                };
                if (extractor != null
                        && surfaceFrame.presentationMode() == ChatPresentationMode.OPEN_PANEL
                        && surfaceFrame.appearance().vanillaStyleInput()
                        && surfaceFrame.appearance().cornerRadius() > 0) {
                    UiPrimitives.withBottomRoundedClip(
                            extractor,
                            viewportBounds,
                            surfaceFrame.appearance().cornerRadius(),
                            paintTimeline);
                } else {
                    paintTimeline.run();
                }
            }
            if (displayMode.foreground && paintsTimeline) {
                chatupgrade$showRichHover(graphics, extractor, font);
            }
        } finally {
            if (ownsClip && extractor != null) {
                ChatUpgradeChatRenderState.endRenderPass(extractor);
            }
        }
    }

    @Unique
    private void chatupgrade$beginClip(
            GuiGraphicsExtractor extractor,
            int screenHeight,
            RichChatViewportMetrics metrics,
            ChatSurfaceFrame surfaceFrame) {
        if (surfaceFrame.presentationMode() == ChatPresentationMode.OPEN_PANEL) {
            RichChatBounds viewport = surfaceFrame.messageViewportBounds();
            ChatUpgradeChatRenderState.beginSurfaceRenderPass(
                    extractor,
                    viewport.left(),
                    viewport.top(),
                    viewport.right(),
                    viewport.bottom());
            return;
        }
        ChatUpgradeChatRenderState.beginRenderPass(
                extractor,
                screenHeight,
                metrics.scale(),
                metrics.visibleHeight(),
                metrics.maxWidth());
    }

    @Unique
    private Matrix3x2f chatupgrade$activePose(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor) {
        if (extractor != null) {
            return new Matrix3x2f(extractor.pose());
        }
        if (graphics instanceof ChatUpgradeClickableTextOnlyGraphicsAccessor clickable) {
            ActiveTextCollector output = clickable.chatupgrade$output();
            if (output != null) {
                return new Matrix3x2f(output.defaultParameters().pose());
            }
        }
        return null;
    }

    @Unique
    private RichChatBounds chatupgrade$viewportBounds(RichChatViewportMetrics metrics) {
        return RichChatBounds.ofSize(
                metrics.backgroundLeft(),
                metrics.chatBottom() - metrics.visibleHeight(),
                metrics.backgroundRight() - metrics.backgroundLeft(),
                metrics.visibleHeight());
    }

    @Unique
    private void chatupgrade$showRichHover(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor,
            Font font) {
        if (extractor == null || !(graphics instanceof ChatUpgradeDrawingFocusedAccessor focused)) {
            return;
        }
        Vector2f local = focused.chatupgrade$localMousePos();
        boolean tooltip = RichChatInteractionRouter.showTooltipForLocalHover(
                extractor,
                font,
                local.x,
                local.y,
                focused.chatupgrade$globalMouseX(),
                focused.chatupgrade$globalMouseY());
        if (tooltip || RichChatInteractionRouter.styleForLocalClick(local.x, local.y) != null) {
            extractor.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    @Unique
    private RichChatViewportMetrics chatupgrade$metrics(
            int screenHeight,
            ChatComponent.DisplayMode displayMode,
            ChatSurfaceFrame surfaceFrame) {
        float textOpacity = minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
        float backgroundOpacity = minecraft.options.textBackgroundOpacity().get().floatValue();
        if (surfaceFrame.presentationMode() == ChatPresentationMode.OPEN_PANEL) {
            RichChatBounds viewport = surfaceFrame.messageViewportBounds();
            int contentInset = 12;
            return RichChatViewportMetrics.forSurface(
                    screenHeight,
                    Math.max(1, viewport.width() - contentInset),
                    viewport.height(),
                    viewport.bottom(),
                    0.0D,
                    textOpacity,
                    backgroundOpacity,
                    true);
        }
        return RichChatViewportMetrics.fromVanilla(
                screenHeight,
                getScale(),
                getWidth(),
                getHeight(),
                0.0D,
                textOpacity,
                backgroundOpacity,
                displayMode.foreground);
    }

}