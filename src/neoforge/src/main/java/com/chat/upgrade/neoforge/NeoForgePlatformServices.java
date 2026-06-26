package com.chat.upgrade.neoforge;

import java.nio.file.Path;

import com.chat.upgrade.platform.PlatformServices;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgePlatformServices implements PlatformServices {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isDedicatedServer() {
        return FMLEnvironment.getDist() == Dist.DEDICATED_SERVER;
    }
}
