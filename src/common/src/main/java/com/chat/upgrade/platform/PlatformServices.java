package com.chat.upgrade.platform;

import java.nio.file.Path;

/**
 * Loader-provided platform services. Implemented by each loader module
 * (Fabric / NeoForge) and installed via {@link Platform#bootstrap(PlatformServices)}.
 */
public interface PlatformServices {
    /** The {@code config/} directory of the running game instance. */
    Path configDir();

    /** True only on a dedicated server JVM (used to gate client-only behaviour such as AWT). */
    boolean isDedicatedServer();
}
