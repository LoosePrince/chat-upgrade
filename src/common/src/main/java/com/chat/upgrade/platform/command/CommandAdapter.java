package com.chat.upgrade.platform.command;

/**
 * Adapts a loader-specific brigadier command source {@code S} to a {@link CommandSink}.
 * The shared command tree is built generically over {@code S} and only touches the source
 * through this adapter.
 */
public interface CommandAdapter<S> {
    CommandSink sink(S source);
}
