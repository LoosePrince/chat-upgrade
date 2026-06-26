package com.chat.upgrade.fabric;

import java.nio.file.Path;

import com.chat.upgrade.platform.PlatformServices;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformServices implements PlatformServices {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
}
