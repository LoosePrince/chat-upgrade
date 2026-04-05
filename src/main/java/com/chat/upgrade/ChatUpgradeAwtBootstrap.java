package com.chat.upgrade;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Minecraft 默认以 AWT headless 启动，导致 {@link java.awt.Toolkit}、文件对话框与剪贴板图片不可用。
 * 在客户端尽可能早地关闭 headless（早于游戏初始化 AWT）。
 */
public final class ChatUpgradeAwtBootstrap implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        // PreLaunch may run before EnvType is reliable; never force GUI on dedicated server JVM.
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            System.setProperty("java.awt.headless", "false");
        }
    }
}
