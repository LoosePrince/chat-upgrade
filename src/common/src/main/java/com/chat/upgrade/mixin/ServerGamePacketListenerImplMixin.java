package com.chat.upgrade.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.server.ServerChatRouteService;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$routeBracketProtocol(ServerboundChatPacket packet, CallbackInfo ci) {
        if (ServerChatRouteService.routeBracketProtocol(player, packet.message())) {
            ci.cancel();
            return;
        }
        ServerChatRouteService.rememberVanillaChat(player, packet.message());
    }
}