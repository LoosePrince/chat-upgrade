package com.chat.upgrade.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

/** Multi-line tooltip text for inline chat images. */
public final class ChatUpgradeImageTooltips {
    private ChatUpgradeImageTooltips() {
    }

    public static Component build(GuiMessage parent, String url, @Nullable ImageEntry entry) {
        List<String> lines = new ArrayList<>();
        lines.add("链接: " + url);
        lines.add("解析格式: " + describeBracketWireFormat());
        lines.add("接收上限: " + ChatUpgradeConfig.formatBytesHuman(ChatUpgradeConfig.get().maxReceiveBytes));
        lines.add("发送来源: " + describeSource(parent));
        lines.add("聊天刻: " + parent.addedTime() + describeTickAge(parent.addedTime()));
        lines.add("消息文本: " + truncate(parent.content().getString(), 120));

        if (entry != null) {
            lines.add("传输体积: " + formatBytes(entry.getFetchedByteLength()));
            String ct = entry.getContentType();
            lines.add("Content-Type: " + (ct != null && !ct.isBlank() ? ct : "—"));
            String md5 = entry.getMd5Hex();
            lines.add("MD5: " + (md5 != null ? md5 : "—"));
            switch (entry.getState()) {
                case LOADING -> lines.add("状态: 加载中 (" + describeLoadPhase(entry) + ")");
                case FAILED -> lines.add("状态: 加载失败");
                case LOADED -> {
                    lines.add("像素尺寸: " + entry.getRawPixelWidth() + "×" + entry.getRawPixelHeight());
                    lines.add("预览绘制: " + entry.getWidth() + "×" + entry.getHeight());
                    lines.add("纹理尺寸: " + entry.getTextureWidth() + "×" + entry.getTextureHeight());
                    String fmt = entry.getDecodedFormatName();
                    lines.add("像素格式: " + (fmt != null ? fmt : "—"));
                }
            }
        } else {
            lines.add("状态: 未缓存");
        }

        lines.add("左键: 在浏览器中打开链接");

        return Component.literal(String.join("\n", lines));
    }

    private static String describeLoadPhase(ImageEntry entry) {
        return switch (entry.getLoadPhase()) {
            case FETCH -> "下载";
            case DECODE -> "解码";
        };
    }

    private static String describeTickAge(int addedTime) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return "";
        }
        int now = mc.gui.getGuiTicks();
        int delta = now - addedTime;
        if (delta < 0) {
            return "";
        }
        return " (距今 " + delta + " 刻)";
    }

    private static String describeSource(GuiMessage parent) {
        return switch (parent.source()) {
            case PLAYER -> "玩家消息";
            case SYSTEM_SERVER -> "服务器系统";
            case SYSTEM_CLIENT -> "客户端系统";
        };
    }

    /**
     * 聊天内联图片在文本里的载荷语法（{@link UpgradeBracketCodec}）。
     */
    private static String describeBracketWireFormat() {
        boolean legacy = ChatUpgradeConfig.get().ciCompatibility;
        String tag = legacy ? UpgradeBracketCodec.WIRE_TAG_LEGACY : UpgradeBracketCodec.WIRE_TAG_NATIVE;
        return "[[" + tag + ",url=<URL>[,name=<名称>]]]";
    }

    private static String formatBytes(int len) {
        if (len < 0) {
            return "—";
        }
        if (len < 1024) {
            return len + " B";
        }
        if (len < 1024 * 1024) {
            return String.format("%.1f KiB", len / 1024.0);
        }
        return String.format("%.2f MiB", len / (1024.0 * 1024.0));
    }

    private static String truncate(String s, int max) {
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max - 1) + "…";
    }
}
