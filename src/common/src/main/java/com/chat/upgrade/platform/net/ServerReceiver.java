package com.chat.upgrade.platform.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@FunctionalInterface
public interface ServerReceiver<P extends CustomPacketPayload> {
    void receive(P payload, ServerPlayContext context);
}
