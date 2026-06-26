package com.chat.upgrade.client.mixin;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.InlineEmojiCodec;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.ui.chat.UpgradeChatHudSync;
import com.chat.upgrade.client.ui.chat.UpgradePhantomCoordinator;
import com.chat.upgrade.client.ui.chat.UpgradePhantomHudLayout;
import com.chat.upgrade.client.ui.chat.state.RichChatIngress;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageSource;
import com.chat.upgrade.client.ui.chat.state.RichChatProjection;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionCoordinator;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionService;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements UpgradeChatHudSync {

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;
    @Shadow
    private int chatScrollbarPos;

    @Shadow
    protected abstract int getWidth();

    @Shadow
    protected abstract int getHeight();

    @Shadow
    protected abstract double getScale();

    @Shadow
    protected abstract int getLineHeight();

    @Shadow
    public abstract int getLinesPerPage();

    @Unique
    private int chatupgrade$sizeBeforeAdd;
    @Unique
    private int chatupgrade$scrollPosBeforeStep;

    @ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component chatupgrade$parsePlayerMessage(Component original) {
        return chatupgrade$processIncoming(original);
    }

    @ModifyVariable(method = "addServerSystemMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component chatupgrade$parseSystemMessage(Component original) {
        return chatupgrade$processIncoming(original);
    }

    @Unique
    private Component chatupgrade$processIncoming(Component original) {
        if (RichChatProjectionCoordinator.hasPending()) {
            InlineEmojiCoordinator.clearPendingSlots();
            return original;
        }
        if (!ChatUpgradeChatPipelineGate.shouldEnhancePlainTextChat()) {
            InlineEmojiCoordinator.clearPendingSlots();
            UpgradeBracketCodec.DecodedBracket decoded = UpgradeBracketCodec.decodeIncoming(original);
            if (decoded.attachment().isPresent() && decoded.attachment().get().hasRenderableUrl()) {
                RichAttachment attachment = decoded.attachment().get();
                RichChatProjection projection = RichChatProjectionService.recordAndProject(
                        "",
                        "",
                        decoded.modified(),
                        decoded.modified().getString(),
                        List.of(attachment),
                        RichChatMessageSource.BRACKET_PROTOCOL);
                RichChatProjectionCoordinator.prepareNext(projection);
                chatupgrade$prepareMediaProjection(projection);
                return decoded.modified();
            }
            return original;
        }
        InlineEmojiCodec.DecodedEmoji emojiDecoded = InlineEmojiCodec.decodeIncoming(original);
        if (emojiDecoded.hasSlots()) {
            InlineEmojiCoordinator.setPendingSlots(emojiDecoded.slots());
        } else {
            InlineEmojiCoordinator.clearPendingSlots();
        }
        UpgradeBracketCodec.DecodedBracket decoded = UpgradeBracketCodec.decodeIncoming(emojiDecoded.modified());
        if (decoded.attachment().isPresent() && decoded.attachment().get().hasRenderableUrl()) {
            RichAttachment attachment = decoded.attachment().get();
            RichChatIngress.record(
                    "",
                    "",
                    decoded.modified(),
                    decoded.modified().getString(),
                    List.of(attachment),
                    emojiDecoded.slots(),
                    RichChatMessageSource.BRACKET_PROTOCOL);
            chatupgrade$preloadMedia(attachment);
            return decoded.modified();
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatIngress.record(
                    "",
                    "",
                    emojiDecoded.modified(),
                    emojiDecoded.modified().getString(),
                    List.of(),
                    emojiDecoded.slots(),
                    RichChatMessageSource.VANILLA_TEXT);
        }
        return emojiDecoded.modified();
    }

    @Unique
    private void chatupgrade$prepareMediaProjection(RichChatProjection projection) {
        RichAttachment attachment = projection.mediaAttachment();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return;
        }
        UpgradePhantomCoordinator.setPendingDecoded(attachment);
        chatupgrade$preloadMedia(attachment);
    }

    @Unique
    private void chatupgrade$preloadMedia(RichAttachment attachment) {
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return;
        }
        String url = attachment.requireRenderableUrl();
        if (attachment.type() == InlineResourceType.IMAGE) {
            if (!ChatUpgradeConfig.get().manualImageReveal) {
                ImageLoader.getOrLoad(url);
            }
        } else if (attachment.type() == InlineResourceType.AUDIO) {
            if (!ChatUpgradeConfig.get().manualAudioReveal) {
                AudioLoader.getOrLoad(url);
            }
        } else {
            if (!ChatUpgradeConfig.get().manualVideoReveal) {
                VideoLoader.getOrLoad(url);
            }
        }
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void chatupgrade$recordSizeBefore(
            Component message, @Nullable MessageSignature sig, @Nullable GuiMessageTag tag,
            CallbackInfo ci) {
        chatupgrade$sizeBeforeAdd = trimmedMessages.size();
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void chatupgrade$recordSizeBeforeSystem(Component message, CallbackInfo ci) {
        chatupgrade$sizeBeforeAdd = trimmedMessages.size();
    }

    @Inject(method = "addPlayerMessage", at = @At("TAIL"))
    private void chatupgrade$insertPhantomLinesPlayer(
            Component message, @Nullable MessageSignature sig, @Nullable GuiMessageTag tag,
            CallbackInfo ci) {
        chatupgrade$insertPhantoms();
    }

    @Inject(method = "addServerSystemMessage", at = @At("TAIL"))
    private void chatupgrade$insertPhantomLinesSystem(Component message, CallbackInfo ci) {
        chatupgrade$insertPhantoms();
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$captureScrollBefore(int dir, CallbackInfo ci) {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatViewport.state().scrollByPixels(dir * Math.max(1, getLineHeight()));
            ChatUpgradeChatRenderState.cancelWheelOverscroll();
            ci.cancel();
            return;
        }
        chatupgrade$scrollPosBeforeStep = chatScrollbarPos;
    }

    @Inject(method = "scrollChat", at = @At("TAIL"))
    private void chatupgrade$animateScroll(int dir, CallbackInfo ci) {
        if (!ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            return;
        }
        int delta = chatScrollbarPos - chatupgrade$scrollPosBeforeStep;
        ChatUpgradeChatRenderState.onScrollDelta(delta, getLineHeight());
    }

    @Inject(method = "resetChatScroll", at = @At("TAIL"))
    private void chatupgrade$resetSmoothScroll(CallbackInfo ci) {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatViewport.state().scrollToBottom();
        }
        ChatUpgradeChatRenderState.resetScrollAnimation();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private void chatupgrade$beginSmoothScrollAndClip(
            net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.gui.Font font,
            int ticks,
            int mouseX,
            int mouseY,
            ChatComponent.DisplayMode displayMode,
            boolean changeCursorOnInsertions,
            CallbackInfo ci) {
        int maxWidth = Mth.ceil(getWidth() / getScale());
        int visibleHeight = getLinesPerPage() * getLineHeight();
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            ChatUpgradeChatRenderState.resetScrollAnimation();
            return;
        }
        if (!ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            ChatUpgradeChatRenderState.resetScrollAnimation();
            return;
        }
        ChatUpgradeChatRenderState.beginRenderPass(graphics, graphics.guiHeight(), getScale(), visibleHeight, maxWidth);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN")
    )
    private void chatupgrade$endSmoothScrollAndClip(
            net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.gui.Font font,
            int ticks,
            int mouseX,
            int mouseY,
            ChatComponent.DisplayMode displayMode,
            boolean changeCursorOnInsertions,
            CallbackInfo ci) {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            ChatUpgradeChatRenderState.endRenderPass(graphics);
        }
    }

    @Redirect(
            method = "forEachLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"
            )
    )
    private int chatupgrade$expandPerPageWhenSubLineVisible(ChatComponent instance) {
        int perPage = getLinesPerPage();
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            return perPage;
        }
        if (!ChatUpgradeChatPipelineGate.shouldUseScrollEnhancements()) {
            return perPage;
        }
        if (Math.abs(ChatUpgradeChatRenderState.smoothOffsetPx()) > 1.0e-3F) {
            return perPage + 1;
        }
        return perPage;
    }

    @Unique
    private void chatupgrade$insertPhantoms() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatProjectionCoordinator.clear();
            UpgradePhantomCoordinator.clear();
            return;
        }
        if (!ChatUpgradeChatPipelineGate.shouldEnhancePlainTextChat()
                && !RichChatProjectionCoordinator.hasPending()
                && !UpgradePhantomCoordinator.hasPendingDecoded()) {
            return;
        }
        RichChatProjection projection = RichChatProjectionCoordinator.consumeNext();
        if (projection != null) {
            if (projection.hasMediaBlock()) {
                chatupgrade$insertProjectedPhantom(projection.mediaAttachment());
            }
            return;
        }
        UpgradePhantomCoordinator.PendingDecoded pending = UpgradePhantomCoordinator.consumePendingDecoded();
        chatupgrade$insertAttachmentPhantom(pending.attachment());
    }

    @Unique
    private void chatupgrade$insertProjectedPhantom(RichAttachment attachment) {
        UpgradePhantomCoordinator.consumePendingDecoded();
        chatupgrade$insertAttachmentPhantom(attachment);
    }

    @Unique
    private void chatupgrade$insertAttachmentPhantom(RichAttachment attachment) {
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return;
        }
        String url = attachment.requireRenderableUrl();
        InlineResourceType type = attachment.type();
        int linesAdded = trimmedMessages.size() - chatupgrade$sizeBeforeAdd;
        if (linesAdded <= 0) {
            return;
        }
        GuiMessage parentMessage = trimmedMessages.get(0).parent();
        switch (type) {
            case AUDIO -> UpgradePhantomHudLayout.onAudioMessageCommitted(url, attachment, parentMessage, trimmedMessages);
            case VIDEO -> UpgradePhantomHudLayout.onVideoMessageCommitted(url, attachment, parentMessage, trimmedMessages);
            case IMAGE -> UpgradePhantomHudLayout.onUrlMessageCommitted(url, attachment, parentMessage, trimmedMessages);
        }
    }

    @Override
    public void refreshInlineLayoutForUrl(String url) {
        if (url.startsWith("audio:")) {
            UpgradePhantomHudLayout.syncLayoutForAudio(url.substring("audio:".length()), trimmedMessages);
        } else if (url.startsWith("video:")) {
            UpgradePhantomHudLayout.syncLayoutForVideo(url.substring("video:".length()), trimmedMessages);
        } else {
            UpgradePhantomHudLayout.syncLayoutForUrl(url, trimmedMessages);
        }
    }

    @Override
    public void requestLayoutSyncForUrl(String url) {
        refreshInlineLayoutForUrl(url);
    }
}
