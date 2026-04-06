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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.client.AudioLoader;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ImageLoader;
import com.chat.upgrade.client.InlineResourceType;
import com.chat.upgrade.client.UpgradeBracketCodec;
import com.chat.upgrade.client.UpgradeChatHudSync;
import com.chat.upgrade.client.UpgradePhantomCoordinator;
import com.chat.upgrade.client.UpgradePhantomHudLayout;
import com.chat.upgrade.client.VideoLoader;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements UpgradeChatHudSync {

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Unique
    private int chatupgrade$sizeBeforeAdd;

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
                AudioLoader.getOrLoad(decoded.url());
            } else {
                VideoLoader.getOrLoad(decoded.url());
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
