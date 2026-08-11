package com.chat.upgrade.net;

import org.jetbrains.annotations.Nullable;

public final class ServerMediaId {
    public static final int HEX_LENGTH = 32;

    private ServerMediaId() {
    }

    public static boolean isValid(@Nullable String value) {
        if (value == null || value.length() != HEX_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean decimal = current >= '0' && current <= '9';
            boolean lowerHex = current >= 'a' && current <= 'f';
            boolean upperHex = current >= 'A' && current <= 'F';
            if (!decimal && !lowerHex && !upperHex) {
                return false;
            }
        }
        return true;
    }
}