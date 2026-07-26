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
        if (!ACTIVE.compareAndSet(false, true)) {
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
            return Optional.empty();
        }
        try {
            return Optional.of(CompletableFuture.supplyAsync(() -> {
                try (Session currentSession = session.get()) {
                    return operation.apply(currentSession);
                }
            }));
        } catch (RuntimeException exception) {
            session.get().close();
            throw exception;
        }
    }

    public static Optional<Path> pickFile(
            Session session,
            String title,
            Set<String> extensions) {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        AtomicReference<Path> selected = new AtomicReference<>();
        Runnable show = () -> selected.set(showOnEventDispatchThread(session, title, extensions));
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                show.run();
            } else {
                SwingUtilities.invokeAndWait(show);
            }
            return Optional.ofNullable(selected.get());
        } catch (Throwable throwable) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: FileDialog invoke error: {}", throwable.toString());
            return Optional.empty();
        } finally {
            session.close();
        }
    }

    private static Path showOnEventDispatchThread(
            Session session,
            String title,
            Set<String> extensions) {
        Frame owner = new Frame();
        FileDialog dialog = null;
        try {
            // FileDialog already creates a native modal child for this AWT owner. Reparenting
            // the owner to Minecraft's GLFW window and disabling that window causes Windows to
            // dismiss the common dialog before it becomes visible on some drivers.
            owner.setUndecorated(true);
            owner.setType(java.awt.Window.Type.UTILITY);
            owner.setBounds(0, 0, 1, 1);
            owner.addNotify();

            dialog = new FileDialog(owner, title, FileDialog.LOAD);
            dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setMultipleMode(false);
            dialog.setFilenameFilter((directory, name) -> hasExtension(name, extensions));
            dialog.setVisible(true);

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
            owner.dispose();
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
        if (minecraft != null && minecraft.mouseHandler instanceof MouseHandlerActiveButtonAccessor access) {
            access.chatupgrade$setActiveButton(null);
        }
    }

    private static void restoreGameInputState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            ACTIVE.set(false);
            return;
        }
        try {
            minecraft.execute(() -> {
                KeyMapping.releaseAll();
                minecraft.mouseHandler.setIgnoreFirstMove();
                ACTIVE.set(false);
            });
        } catch (RuntimeException exception) {
            ACTIVE.set(false);
            ChatUpgrade.LOGGER.warn("ChatUpgrade: failed to restore input state: {}", exception.toString());
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
                return;
            }
            restoreGameInputState();
        }
    }
}