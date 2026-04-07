package com.chat.upgrade;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chat.upgrade.server.ServerMediaServerNetworking;
import com.chat.upgrade.net.ServerMediaPayloads;

public class ChatUpgrade implements ModInitializer {
	public static final String MOD_ID = "chat-upgrade";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerMediaPayloads.registerAll();
		ServerMediaServerNetworking.init();
	}
}