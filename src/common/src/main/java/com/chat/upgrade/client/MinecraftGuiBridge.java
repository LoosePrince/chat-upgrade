package com.chat.upgrade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.Nullable;

/**
 * Version-specific access to {@link Minecraft} GUI/HUD APIs (26.1 vs 26.2 Gui/Hud split).
 */
public final class MinecraftGuiBridge {
    private MinecraftGuiBridge() {
    }

    public static boolean hasGui(@Nullable Minecraft minecraft) {
        return minecraft != null && minecraft.gui != null;
    }

    public static @Nullable ChatComponent chat(@Nullable Minecraft minecraft) {
        if (minecraft == null || minecraft.gui == null) {
            return null;
        }
        //? if >=26.2 {
        return minecraft.gui.hud == null ? null : minecraft.gui.hud.getChat();
        //? } else {
        /* return minecraft.gui.getChat(); */
        //? }
    }

    public static int guiTicks(@Nullable Minecraft minecraft) {
        if (minecraft == null || minecraft.gui == null) {
            return 0;
        }
        //? if >=26.2 {
        return minecraft.gui.hud == null ? 0 : minecraft.gui.hud.getGuiTicks();
        //? } else {
        /* return minecraft.gui.getGuiTicks(); */
        //? }
    }

    public static void setScreen(@Nullable Minecraft minecraft, @Nullable Screen screen) {
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        //? if >=26.2 {
        minecraft.gui.setScreen(screen);
        //? } else {
        /* minecraft.setScreen(screen); */
        //? }
    }

    public static @Nullable Screen currentScreen(@Nullable Minecraft minecraft) {
        if (minecraft == null || minecraft.gui == null) {
            return null;
        }
        //? if >=26.2 {
        return minecraft.gui.screen();
        //? } else {
        /* return minecraft.screen; */
        //? }
    }

    public static boolean isCurrentScreen(@Nullable Minecraft minecraft, Screen screen) {
        return currentScreen(minecraft) == screen;
    }
}
