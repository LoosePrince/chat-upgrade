package com.chat.upgrade.client.ui.chat.state;

public enum ChatTimelineGroupPosition {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST;

    public boolean startsGroup() {
        return this == SINGLE || this == FIRST;
    }

    public boolean endsGroup() {
        return this == SINGLE || this == LAST;
    }
}