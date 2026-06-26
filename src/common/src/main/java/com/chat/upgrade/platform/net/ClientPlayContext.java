package com.chat.upgrade.platform.net;

/** Context handed to a client-side payload handler. */
public interface ClientPlayContext {
    /** Run the task on the client (render) thread. */
    void execute(Runnable task);
}
