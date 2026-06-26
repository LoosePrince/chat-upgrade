package com.chat.upgrade.fabric;

import com.chat.upgrade.platform.Platform;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Minecraft starts with AWT in headless mode, which breaks file dialogs / clipboard image access.
 * Disable headless as early as possible on the client (before the game initialises AWT).
 */
public final class ChatUpgradeAwtBootstrapFabric implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        Platform.bootstrap(new FabricPlatformServices());
        if (!Platform.isDedicatedServer()) {
            System.setProperty("java.awt.headless", "false");
        }
    }
}
