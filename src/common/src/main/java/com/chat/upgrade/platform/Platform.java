package com.chat.upgrade.platform;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Static access point for {@link PlatformServices}. Each loader installs its implementation
 * as early as possible (Fabric pre-launch / NeoForge mod construction) via {@link #bootstrap}.
 */
public final class Platform {
    private static volatile PlatformServices services;

    private Platform() {
    }

    public static void bootstrap(PlatformServices impl) {
        if (services == null) {
            services = Objects.requireNonNull(impl, "platform services");
        }
    }

    private static PlatformServices services() {
        PlatformServices current = services;
        if (current == null) {
            throw new IllegalStateException("chat-upgrade: Platform.bootstrap(...) was not called");
        }
        return current;
    }

    public static Path configDir() {
        return services().configDir();
    }

    public static boolean isDedicatedServer() {
        return services().isDedicatedServer();
    }
}
