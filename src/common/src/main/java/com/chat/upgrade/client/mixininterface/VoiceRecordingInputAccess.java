package com.chat.upgrade.client.mixininterface;

/** Receives raw release events that may bypass {@code ChatScreen} overrides. */
public interface VoiceRecordingInputAccess {
    boolean chatupgrade$releaseVoiceMouse(int button);

    boolean chatupgrade$releaseVoiceShortcut(int key);
}