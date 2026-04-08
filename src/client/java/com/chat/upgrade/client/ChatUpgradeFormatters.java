package com.chat.upgrade.client;

import java.util.Locale;

public final class ChatUpgradeFormatters {
    private ChatUpgradeFormatters() {
    }

    public static String formatBytes(int len) {
        if (len < 0) {
            return "—";
        }
        if (len < 1024) {
            return len + " B";
        }
        if (len < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KiB", len / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MiB", len / (1024.0 * 1024.0));
    }

    public static String formatMs(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long m = s / 60L;
        long r = s % 60L;
        return String.format(Locale.ROOT, "%d:%02d", m, r);
    }
}
