package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.AudioControlClickEvent;
import com.chat.upgrade.client.AudioLoader;
import com.chat.upgrade.client.AudioPlayerService;
import com.chat.upgrade.client.ImageLoader;
import com.chat.upgrade.client.ManualRevealClickEvent;
import com.chat.upgrade.client.UpgradeChatHudSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * MC 26.1+ removed {@code Screen.handleClickEvent}; chat link handling goes through
 * {@link ChatScreen}'s private {@code handleComponentClicked(Style, boolean)}.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenManualRevealMixin {

    @Inject(
            method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chatupgrade$manualRevealImageClick(Style style, boolean insertionClick, CallbackInfoReturnable<Boolean> cir) {
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
        if (ChatUpgradeConfig.get().manualImageReveal) {
            Optional<String> urlOpt = ManualRevealClickEvent.parseUrl(clickEvent);
            if (urlOpt.isEmpty()) {
                return;
            }
            String url = urlOpt.get();
            ImageLoader.getOrLoad(url);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.getChat() instanceof UpgradeChatHudSync sync) {
                sync.requestLayoutSyncForUrl(url);
            }
            cir.setReturnValue(true);
        }
    }
}
