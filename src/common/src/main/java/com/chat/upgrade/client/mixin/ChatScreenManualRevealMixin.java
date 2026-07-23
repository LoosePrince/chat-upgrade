package com.chat.upgrade.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.AudioControlClickEvent;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindowClickEvent;
import com.chat.upgrade.client.ui.chat.AudioOptionsClickEvent;
import com.chat.upgrade.client.ui.chat.CompactAudioOptionsMenu;
import com.chat.upgrade.client.ui.chat.ImagePreviewClickEvent;
import com.chat.upgrade.client.ui.chat.ManualRevealClickEvent;
import com.chat.upgrade.client.ui.chat.UpgradeChatHudSync;
import com.chat.upgrade.client.ui.chat.VideoControlClickEvent;
import com.chat.upgrade.client.ui.chat.VideoPreviewClickEvent;
import com.chat.upgrade.client.ui.screen.ImagePreviewScreen;
import com.chat.upgrade.client.ui.screen.VideoPreviewScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
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
            ChatComponent chat = MinecraftGuiBridge.chat(Minecraft.getInstance());
            if (chat instanceof UpgradeChatHudSync sync) {
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
        Optional<AudioOptionsClickEvent.Parsed> audioOptions = AudioOptionsClickEvent.parse(clickEvent);
        if (audioOptions.isPresent()) {
            AudioOptionsClickEvent.Parsed parsed = audioOptions.get();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                CompactAudioOptionsMenu.toggle(
                        parsed.url(),
                        parsed.name(),
                        parsed.anchorX(),
                        parsed.anchorY(),
                        minecraft.font,
                        minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight());
            }
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
            ChatComponent chat = MinecraftGuiBridge.chat(Minecraft.getInstance());
            if (chat instanceof UpgradeChatHudSync sync) {
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
        String layoutKey = reveal.url();
        if (reveal.type() == InlineResourceType.IMAGE) {
            ImageLoader.forceReload(reveal.url());
        } else if (reveal.type() == InlineResourceType.AUDIO) {
            AudioLoader.forceReload(reveal.url());
            layoutKey = "audio:" + reveal.url();
        } else {
            VideoLoader.forceReload(reveal.url());
            layoutKey = "video:" + reveal.url();
        }
        Minecraft minecraft = Minecraft.getInstance();
        ChatComponent chat = MinecraftGuiBridge.chat(minecraft);
        if (chat instanceof UpgradeChatHudSync sync) {
            sync.requestLayoutSyncForUrl(layoutKey);
        }
        cir.setReturnValue(true);
    }
}
