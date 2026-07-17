package com.chat.upgrade.client.ui.chat.state;

public enum ChatMessageKind {
    PLAYER,
    SYSTEM,
    GAME,
    ANNOUNCEMENT,
    ERROR;

    public boolean playerAuthored() {
        return this == PLAYER;
    }

    public boolean systemLike() {
        return this != PLAYER;
    }
}