package com.chat.upgrade.platform.net;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Context handed to a server-side payload handler. */
public interface ServerPlayContext {
    /** Run the task on the server thread. */
    void execute(Runnable task);

    /** The player that sent the payload. */
    ServerPlayer player();

    /** The server the payload was received on. */
    MinecraftServer server();
}
