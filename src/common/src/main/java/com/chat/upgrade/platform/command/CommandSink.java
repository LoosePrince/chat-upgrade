package com.chat.upgrade.platform.command;

import net.minecraft.network.chat.Component;

/** Loader-agnostic feedback channel for a client command invocation. */
public interface CommandSink {
    void feedback(Component message);

    void error(Component message);
}
