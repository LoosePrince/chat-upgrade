package com.chat.upgrade.fabric;

import com.chat.upgrade.platform.command.CommandAdapter;
import com.chat.upgrade.platform.command.CommandSink;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class FabricCommandAdapter implements CommandAdapter<FabricClientCommandSource> {
    @Override
    public CommandSink sink(FabricClientCommandSource source) {
        return new CommandSink() {
            @Override
            public void feedback(Component message) {
                source.sendFeedback(message);
            }

            @Override
            public void error(Component message) {
                source.sendError(message);
            }
        };
    }
}
