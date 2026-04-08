package com.chat.upgrade.client.media.audio;

import java.util.function.Consumer;

public final class SingleActivePlaybackCoordinator {
    private volatile String activeUrl;

    public synchronized void activate(String url, Consumer<String> pauseOther) {
        String current = activeUrl;
        if (current != null && !current.equals(url)) {
            pauseOther.accept(current);
        }
        activeUrl = url;
    }

    public synchronized void deactivateIfActive(String url) {
        if (url.equals(activeUrl)) {
            activeUrl = null;
        }
    }

    public synchronized void clear() {
        activeUrl = null;
    }
}
