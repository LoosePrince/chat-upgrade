package com.chat.upgrade.client;

import java.util.function.Consumer;

final class SingleActivePlaybackCoordinator {
    private volatile String activeUrl;

    synchronized void activate(String url, Consumer<String> pauseOther) {
        String current = activeUrl;
        if (current != null && !current.equals(url)) {
            pauseOther.accept(current);
        }
        activeUrl = url;
    }

    synchronized void deactivateIfActive(String url) {
        if (url.equals(activeUrl)) {
            activeUrl = null;
        }
    }

    synchronized void clear() {
        activeUrl = null;
    }
}
