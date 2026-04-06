package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.AudioLoader;
import com.chat.upgrade.client.ImageLoader;
import com.chat.upgrade.client.InlineResourceType;
import com.chat.upgrade.client.UpgradeBracketCodec;
import com.chat.upgrade.client.UpgradeChatHudSync;
import com.chat.upgrade.client.UpgradePhantomCoordinator;
import com.chat.upgrade.client.UpgradePhantomHudLayout;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements UpgradeChatHudSync {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Unique private int chatupgrade$sizeBeforeAdd;

    @ModifyVariable(
            method = "addPlayerMessage",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component chatupgrade$parsePlayerMessage(Component original) {
        return chatupgrade$processIncoming(original);
    }

    @ModifyVariable(
            method = "addServerSystemMessage",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component chatupgrade$parseSystemMessage(Component original) {
        return chatupgrade$processIncoming(original);
    }

    @Unique
    private Component chatupgrade$processIncoming(Component original) {
        UpgradeBracketCodec.DecodedBracket decoded = UpgradeBracketCodec.decodeIncoming(original);
        if (decoded.hasUrl()) {
            UpgradePhantomCoordinator.pendingDecodedUrl = decoded.url();
            UpgradePhantomCoordinator.pendingDecodedType = decoded.resourceType();
            if (decoded.resourceType() == InlineResourceType.IMAGE) {
                if (!ChatUpgradeConfig.get().manualImageReveal) {
                    ImageLoader.getOrLoad(decoded.url());
                }
            } else {
                AudioLoader.getOrLoad(decoded.url());
            }
            return decoded.modified();
        }
        return original;
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void chatupgrade$recordSizeBefore(
            Component message, @Nullable MessageSignature sig, @Nullable GuiMessageTag tag,
            CallbackInfo ci
    ) {
        chatupgrade$sizeBeforeAdd = trimmedMessages.size();
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void chatupgrade$recordSizeBeforeSystem(Component message, CallbackInfo ci) {
        chatupgrade$sizeBeforeAdd = trimmedMessages.size();
    }

    @Inject(method = "addPlayerMessage", at = @At("TAIL"))
    private void chatupgrade$insertPhantomLinesPlayer(
            Component message, @Nullable MessageSignature sig, @Nullable GuiMessageTag tag,
            CallbackInfo ci
    ) {
        chatupgrade$insertPhantoms();
    }

    @Inject(method = "addServerSystemMessage", at = @At("TAIL"))
    private void chatupgrade$insertPhantomLinesSystem(Component message, CallbackInfo ci) {
        chatupgrade$insertPhantoms();
    }

    @Unique
    private void chatupgrade$insertPhantoms() {
        String url = UpgradePhantomCoordinator.pendingDecodedUrl;
        InlineResourceType type = UpgradePhantomCoordinator.pendingDecodedType;
        UpgradePhantomCoordinator.pendingDecodedUrl = null;
        UpgradePhantomCoordinator.pendingDecodedType = InlineResourceType.IMAGE;
        if (url == null) {
            return;
        }
        int linesAdded = trimmedMessages.size() - chatupgrade$sizeBeforeAdd;
        if (linesAdded <= 0) {
            return;
        }
        GuiMessage parentMessage = trimmedMessages.get(0).parent();
        if (type == InlineResourceType.AUDIO) {
            UpgradePhantomHudLayout.onAudioMessageCommitted(url, parentMessage, trimmedMessages);
        } else {
            UpgradePhantomHudLayout.onUrlMessageCommitted(url, parentMessage, trimmedMessages);
        }
    }

    @Override
    public void refreshInlineLayoutForUrl(String url) {
        if (url.startsWith("audio:")) {
            UpgradePhantomHudLayout.syncLayoutForAudio(url.substring("audio:".length()), trimmedMessages);
        } else {
            UpgradePhantomHudLayout.syncLayoutForUrl(url, trimmedMessages);
        }
    }

    @Override
    public void requestLayoutSyncForUrl(String url) {
        UpgradePhantomHudLayout.syncLayoutForUrl(url, trimmedMessages);
    }
}
