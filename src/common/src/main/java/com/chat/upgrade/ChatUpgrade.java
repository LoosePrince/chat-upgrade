package com.chat.upgrade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chat.upgrade.server.ServerMediaServerNetworking;

/**
 * Loader-agnostic mod entry. Loader main entry points (Fabric {@code ModInitializer} /
 * NeoForge {@code @Mod}) call {@link #init()} for the common server-side bootstrap, then perform
 * loader-specific payload/event wiring.
 */
public final class ChatUpgrade {
    public static final String MOD_ID = "chatupgrade";
    public static final Logger LOGGER = LoggerFactory.getLogger("Chat Upgrade");

    private ChatUpgrade() {
    }

    /** Common server-side bootstrap (config + media store). Loader-agnostic; safe on both sides. */
    public static void init() {
        ServerMediaServerNetworking.init();
    }
}
