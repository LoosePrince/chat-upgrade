package com.chat.upgrade.client.ui.chat.input;

import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.mixin.MouseHandlerActiveButtonAccessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class NativeFileDialogModal {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private NativeFileDialogModal() {
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static Optional<Session> tryOpen(long glfwWindowHandle) {
        boolean opened = ACTIVE.compareAndSet(false, true);
        ChatUpgrade.LOGGER.info(
                "chat-upgrade: file dialog session requested (activeBefore={}, opened={}, glfwWindow={})",
                !opened,
                opened,
                glfwWindowHandle);
        if (!opened) {
            return Optional.empty();
        }
        releaseGameInputState();
        return Optional.of(new Session(glfwWindowHandle));
    }

    public static <T> Optional<CompletableFuture<T>> supplyAsync(
            long glfwWindowHandle,
            Function<Session, T> operation) {
        Optional<Session> session = tryOpen(glfwWindowHandle);
        if (session.isEmpty()) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: file dialog request rejected because another session is active");
            return Optional.empty();
        }
        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                ChatUpgrade.LOGGER.info("chat-upgrade: file dialog worker started");
                try (Session currentSession = session.get()) {
                    return operation.apply(currentSession);
                }
            });
            future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    ChatUpgrade.LOGGER.info("chat-upgrade: file dialog worker completed");
                } else {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: file dialog worker failed", throwable);
                }
            });
            return Optional.of(future);
        } catch (RuntimeException exception) {
            session.get().close();
            throw exception;
        }
    }

    public static Optional<Path> pickFile(
            Session session,
            String title,
            Set<String> extensions) {
        ChatUpgrade.LOGGER.info(
                "chat-upgrade: file dialog picker entered (headless={}, onEdt={}, title={}, extensionCount={})",
                GraphicsEnvironment.isHeadless(),
                SwingUtilities.isEventDispatchThread(),
                title,
                extensions.size());
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        AtomicReference<Path> selected = new AtomicReference<>();
        Runnable show = () -> {
            ChatUpgrade.LOGGER.info("chat-upgrade: file dialog executing on AWT event thread");
            selected.set(showOnEventDispatchThread(session, title, extensions));
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                show.run();
            } else {
                ChatUpgrade.LOGGER.info("chat-upgrade: file dialog waiting for AWT event thread");
                SwingUtilities.invokeAndWait(show);
            }
            ChatUpgrade.LOGGER.info("chat-upgrade: file dialog AWT invocation returned (selected={})", selected.get() != null);
            return Optional.ofNullable(selected.get());
        } catch (Throwable throwable) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: file dialog AWT invocation failed", throwable);
            return Optional.empty();
        } finally {
            ChatUpgrade.LOGGER.info("chat-upgrade: file dialog picker closing session");
            session.close();
        }
    }

    private static Path showOnEventDispatchThread(
            Session session,
            String title,
            Set<String> extensions) {
        FileDialog dialog = null;
        try {
            // An unowned dialog avoids retaining an invisible AWT owner between selections.
            // Input is already captured by the modal session while the native window is open.
            ChatUpgrade.LOGGER.info("chat-upgrade: creating unowned native file dialog");
            dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
            dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setMultipleMode(false);
            dialog.setFilenameFilter((directory, name) -> hasExtension(name, extensions));
            ChatUpgrade.LOGGER.info(
                    "chat-upgrade: showing native file dialog (displayable={}, modalType={})",
                    dialog.isDisplayable(),
                    dialog.getModalityType());
            dialog.setVisible(true);
            ChatUpgrade.LOGGER.info("chat-upgrade: native file dialog closed (displayable={})", dialog.isDisplayable());

            String directory = dialog.getDirectory();
            String file = dialog.getFile();
            if (directory == null || file == null || file.isBlank()) {
                ChatUpgrade.LOGGER.info("chat-upgrade: native file dialog closed without a selection");
                return null;
            }
            Path path = Path.of(directory, file);
            ChatUpgrade.LOGGER.info("chat-upgrade: native file dialog selected {}", path);
            return path;
        } catch (Throwable throwable) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: native file dialog failed", throwable);
            return null;
        } finally {
            if (dialog != null) {
                dialog.dispose();
            }
        }
    }

    private static boolean hasExtension(String name, Set<String> extensions) {
        if (name == null) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return false;
        }
        return extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void releaseGameInputState() {
        KeyMapping.releaseAll();
        Minecraft minecraft = Minecraft.getInstance();
        ChatUpgrade.LOGGER.info("chat-upgrade: file dialog input released (minecraftPresent={})", minecraft != null);
        if (minecraft != null && minecraft.mouseHandler instanceof MouseHandlerActiveButtonAccessor access) {
            access.chatupgrade$setActiveButton(null);
        }
    }

    private static void restoreGameInputState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            ACTIVE.set(false);
            ChatUpgrade.LOGGER.info("chat-upgrade: file dialog input restored without a Minecraft instance");
            return;
        }
        try {
            ChatUpgrade.LOGGER.info("chat-upgrade: file dialog input restore queued on render thread");
            minecraft.execute(() -> {
                KeyMapping.releaseAll();
                minecraft.mouseHandler.setIgnoreFirstMove();
                ACTIVE.set(false);
                ChatUpgrade.LOGGER.info("chat-upgrade: file dialog input restored on render thread");
            });
        } catch (RuntimeException exception) {
            ACTIVE.set(false);
            ChatUpgrade.LOGGER.warn("chat-upgrade: file dialog input restore failed", exception);
        }
    }

    public static final class Session implements AutoCloseable {
        private final long glfwWindowHandle;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(long glfwWindowHandle) {
            this.glfwWindowHandle = glfwWindowHandle;
        }

        public long glfwWindowHandle() {
            return glfwWindowHandle;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                ChatUpgrade.LOGGER.info("chat-upgrade: file dialog session close ignored because it is already closed");
                return;
            }
            ChatUpgrade.LOGGER.info("chat-upgrade: file dialog session closing (glfwWindow={})", glfwWindowHandle);
            restoreGameInputState();
        }
    }
}