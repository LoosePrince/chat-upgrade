package com.chat.upgrade.client.ui.settings;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public sealed interface SettingsOption permits SettingsOption.BooleanOption, SettingsOption.ColorOption,
        SettingsOption.EnumOption, SettingsOption.HeadingOption, SettingsOption.IntOption,
        SettingsOption.TextOption {
    String labelKey();

    record HeadingOption(String labelKey) implements SettingsOption {
    }

    record BooleanOption(
            String labelKey,
            String descriptionKey,
            BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter) implements SettingsOption {
        public BooleanOption(
                String labelKey,
                BooleanSupplier getter,
                java.util.function.Consumer<Boolean> setter) {
            this(labelKey, "", getter, setter);
        }
    }

    record IntOption(
            String labelKey,
            IntSupplier getter,
            IntConsumer setter,
            int min,
            int max,
            ValueFormat format) implements SettingsOption {
        public IntOption {
            if (max < min) {
                throw new IllegalArgumentException("max must be greater than or equal to min");
            }
            format = format == null ? ValueFormat.INTEGER : format;
        }
    }

    record EnumOption(
            String labelKey,
            IntSupplier selectedIndex,
            IntConsumer selectIndex,
            List<String> valueLabelKeys) implements SettingsOption {
        public EnumOption {
            valueLabelKeys = List.copyOf(valueLabelKeys);
            if (valueLabelKeys.isEmpty()) {
                throw new IllegalArgumentException("enum option must provide values");
            }
        }
    }

    record ColorOption(String labelKey, IntSupplier getter, IntConsumer setter) implements SettingsOption {
    }

    record TextOption(
            String labelKey,
            String descriptionKey,
            Supplier<String> getter,
            Consumer<String> setter,
            int maxLength) implements SettingsOption {
        public TextOption {
            if (maxLength < 1) {
                throw new IllegalArgumentException("maxLength must be positive");
            }
        }
    }

    enum ValueFormat {
        INTEGER,
        PERCENT,
        PIXELS,
        MEBIBYTES
    }
}