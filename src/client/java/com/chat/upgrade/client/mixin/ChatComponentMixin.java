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
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.ui.chat.UpgradeChatHudSync;
import com.chat.upgrade.client.ui.chat.UpgradePhantomCoordinator;
import com.chat.upgrade.client.ui.chat.UpgradePhantomHudLayout;

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
        UpgradeBracketCodec.DecodedBracket decoded = UpgradeBracketCodec.decodeIncoming(original);
        if (decoded.hasUrl()) {
            UpgradePhantomCoordinator.setPendingDecoded(decoded.url(), decoded.name(), decoded.resourceType());
            if (decoded.resourceType() == InlineResourceType.IMAGE) {
                if (!ChatUpgradeConfig.get().manualImageReveal) {
                    ImageLoader.getOrLoad(decoded.url());
                }
            } else if (decoded.resourceType() == InlineResourceType.AUDIO) {
                if (!ChatUpgradeConfig.get().manualAudioReveal) {
                    AudioLoader.getOrLoad(decoded.url());
                }
            } else {
                if (!ChatUpgradeConfig.get().manualVideoReveal) {
                    VideoLoader.getOrLoad(decoded.url());
                }
            }
            return decoded.modified();
        }
        return original;
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

    @Inject(method = "scrollChat", at = @At("HEAD"))
    private void chatupgrade$captureScrollBefore(int dir, CallbackInfo ci) {
        chatupgrade$scrollPosBeforeStep = chatScrollbarPos;
    }

    @Inject(method = "scrollChat", at = @At("TAIL"))
    private void chatupgrade$animateScroll(int dir, CallbackInfo ci) {
        int delta = chatScrollbarPos - chatupgrade$scrollPosBeforeStep;
        ChatUpgradeChatRenderState.onScrollDelta(delta, getLineHeight());
    }

    @Inject(method = "resetChatScroll", at = @At("TAIL"))
    private void chatupgrade$resetSmoothScroll(CallbackInfo ci) {
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
        ChatUpgradeChatRenderState.endRenderPass(graphics);
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
        if (Math.abs(ChatUpgradeChatRenderState.smoothOffsetPx()) > 1.0e-3F) {
            return perPage + 1;
        }
        return perPage;
    }

    @Unique
    private void chatupgrade$insertPhantoms() {
        UpgradePhantomCoordinator.PendingDecoded pending = UpgradePhantomCoordinator.consumePendingDecoded();
        String url = pending.url();
        String name = pending.name();
        InlineResourceType type = pending.type();
        if (url == null) {
            return;
        }
        int linesAdded = trimmedMessages.size() - chatupgrade$sizeBeforeAdd;
        if (linesAdded <= 0) {
            return;
        }
        UpgradePhantomCoordinator.prepareNextPhantomTop(type, name, null);
        GuiMessage parentMessage = trimmedMessages.get(0).parent();
        switch (type) {
            case AUDIO -> UpgradePhantomHudLayout.onAudioMessageCommitted(url, parentMessage, trimmedMessages);
            case VIDEO -> UpgradePhantomHudLayout.onVideoMessageCommitted(url, parentMessage, trimmedMessages);
            case IMAGE -> UpgradePhantomHudLayout.onUrlMessageCommitted(url, parentMessage, trimmedMessages);
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
