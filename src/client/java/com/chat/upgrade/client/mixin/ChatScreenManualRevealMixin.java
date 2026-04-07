package com.chat.upgrade.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.AudioControlClickEvent;
import com.chat.upgrade.client.AudioFloatingWindow;
import com.chat.upgrade.client.AudioFloatingWindowClickEvent;
import com.chat.upgrade.client.AudioLoader;
import com.chat.upgrade.client.AudioPlayerService;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ImagePreviewClickEvent;
import com.chat.upgrade.client.ImagePreviewScreen;
import com.chat.upgrade.client.ImageLoader;
import com.chat.upgrade.client.InlineResourceType;
import com.chat.upgrade.client.ManualRevealClickEvent;
import com.chat.upgrade.client.UpgradeChatHudSync;
import com.chat.upgrade.client.VideoControlClickEvent;
import com.chat.upgrade.client.VideoLoader;
import com.chat.upgrade.client.VideoPreviewClickEvent;
import com.chat.upgrade.client.VideoPreviewScreen;
import com.chat.upgrade.client.VideoPlayerService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

/**
 * MC 26.1+ removed {@code Screen.handleClickEvent}; chat link handling goes
 * through
 * {@link ChatScreen}'s private {@code handleComponentClicked(Style, boolean)}.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenManualRevealMixin {

    @Inject(method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;Z)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$manualRevealImageClick(Style style, boolean insertionClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (insertionClick) {
            return;
        }
        if (style == null) {
            return;
        }
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return;
        }
        Optional<AudioControlClickEvent.Parsed> audioOpt = AudioControlClickEvent.parse(clickEvent);
        if (audioOpt.isPresent()) {
            AudioControlClickEvent.Parsed p = audioOpt.get();
            AudioLoader.getOrLoad(p.url());
            switch (p.action()) {
                case TOGGLE -> AudioPlayerService.toggle(p.url());
                case TOGGLE_LOOP -> AudioPlayerService.toggleLoop(p.url());
                case SEEK -> AudioPlayerService.seek(p.url(), p.ratio());
            }
            if (Minecraft.getInstance().gui.getChat() instanceof UpgradeChatHudSync sync) {
                sync.requestLayoutSyncForUrl("audio:" + p.url());
            }
            cir.setReturnValue(true);
            return;
        }
        Optional<AudioFloatingWindowClickEvent.Parsed> audioFloatingOpt = AudioFloatingWindowClickEvent.parse(clickEvent);
        if (audioFloatingOpt.isPresent()) {
            AudioFloatingWindowClickEvent.Parsed parsed = audioFloatingOpt.get();
            AudioFloatingWindow.toggleFor(parsed.url(), parsed.name());
            cir.setReturnValue(true);
            return;
        }
        Optional<VideoControlClickEvent.Parsed> videoOpt = VideoControlClickEvent.parse(clickEvent);
        if (videoOpt.isPresent()) {
            VideoControlClickEvent.Parsed p = videoOpt.get();
            VideoLoader.getOrLoad(p.url());
            switch (p.action()) {
                case TOGGLE -> VideoPlayerService.toggle(p.url());
                case SEEK -> VideoPlayerService.seek(p.url(), p.ratio());
            }
            if (Minecraft.getInstance().gui.getChat() instanceof UpgradeChatHudSync sync) {
                sync.requestLayoutSyncForUrl("video:" + p.url());
            }
            cir.setReturnValue(true);
            return;
        }
        Optional<VideoPreviewClickEvent.Parsed> videoPreviewOpt = VideoPreviewClickEvent.parse(clickEvent);
        if (videoPreviewOpt.isPresent()) {
            VideoPreviewClickEvent.Parsed p = videoPreviewOpt.get();
            VideoPreviewScreen.open(p.url(), p.name());
            cir.setReturnValue(true);
            return;
        }
        Optional<ImagePreviewClickEvent.Parsed> imagePreviewOpt = ImagePreviewClickEvent.parse(clickEvent);
        if (imagePreviewOpt.isPresent()) {
            ImagePreviewClickEvent.Parsed p = imagePreviewOpt.get();
            ImagePreviewScreen.open(p.url(), p.name());
            cir.setReturnValue(true);
            return;
        }
        Optional<ManualRevealClickEvent.Parsed> revealOpt = ManualRevealClickEvent.parse(clickEvent);
        if (revealOpt.isEmpty()) {
            return;
        }
        ManualRevealClickEvent.Parsed reveal = revealOpt.get();
        boolean enabled = switch (reveal.type()) {
            case IMAGE -> ChatUpgradeConfig.get().manualImageReveal;
            case AUDIO -> ChatUpgradeConfig.get().manualAudioReveal;
            case VIDEO -> ChatUpgradeConfig.get().manualVideoReveal;
        };
        if (!enabled) {
            return;
        }
        String layoutKey = reveal.url();
        if (reveal.type() == InlineResourceType.IMAGE) {
            ImageLoader.getOrLoad(reveal.url());
        } else if (reveal.type() == InlineResourceType.AUDIO) {
            AudioLoader.getOrLoad(reveal.url());
            layoutKey = "audio:" + reveal.url();
        } else {
            VideoLoader.getOrLoad(reveal.url());
            layoutKey = "video:" + reveal.url();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.getChat() instanceof UpgradeChatHudSync sync) {
            sync.requestLayoutSyncForUrl(layoutKey);
        }
        cir.setReturnValue(true);
    }
}
