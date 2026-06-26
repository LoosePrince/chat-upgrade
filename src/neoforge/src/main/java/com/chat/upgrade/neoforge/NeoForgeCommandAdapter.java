package com.chat.upgrade.neoforge;

import com.chat.upgrade.platform.command.CommandAdapter;
import com.chat.upgrade.platform.command.CommandSink;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class NeoForgeCommandAdapter implements CommandAdapter<CommandSourceStack> {
    @Override
    public CommandSink sink(CommandSourceStack source) {
        return new CommandSink() {
            @Override
            public void feedback(Component message) {
                source.sendSuccess(() -> message, false);
            }

            @Override
            public void error(Component message) {
                source.sendFailure(message);
            }
        };
    }
}
