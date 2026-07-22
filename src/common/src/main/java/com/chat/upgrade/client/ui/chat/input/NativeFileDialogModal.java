package com.chat.upgrade.client.ui.chat.input;

import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import org.lwjgl.glfw.GLFWNativeWin32;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.mixin.MouseHandlerActiveButtonAccessor;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.W32APIOptions;

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
        return Optional.of(new Session(glfwWindowHandle, resolveNativeGameWindow(glfwWindowHandle)));
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
        WindowsOwnerBinding binding = WindowsOwnerBinding.none();
        try {
            owner.setUndecorated(true);
            owner.setType(java.awt.Window.Type.UTILITY);
            owner.setBounds(0, 0, 1, 1);
            owner.addNotify();
            binding = WindowsOwnerBinding.attach(owner, session.nativeGameWindow());
            WindowsOwnerBinding currentBinding = binding;

            dialog = new FileDialog(owner, title, FileDialog.LOAD);
            dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setMultipleMode(false);
            dialog.setAlwaysOnTop(true);
            dialog.setFilenameFilter((directory, name) -> hasExtension(name, extensions));
            FileDialog currentDialog = dialog;
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowOpened(java.awt.event.WindowEvent event) {
                    currentDialog.toFront();
                    currentBinding.bringDialogToFront(currentDialog);
                }
            });
            dialog.setVisible(true);

            String directory = dialog.getDirectory();
            String file = dialog.getFile();
            if (directory == null || file == null || file.isBlank()) {
                return null;
            }
            return Path.of(directory, file);
        } catch (Throwable throwable) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: FileDialog error: {}", throwable.toString());
            return null;
        } finally {
            if (dialog != null) {
                dialog.dispose();
            }
            binding.close();
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

    private static long resolveNativeGameWindow(long glfwWindowHandle) {
        if (!isWindows() || glfwWindowHandle == 0L) {
            return 0L;
        }
        try {
            return GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
        } catch (Throwable throwable) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade: failed to resolve Win32 game window: {}", throwable.toString());
            return 0L;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
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
        private final long nativeGameWindow;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(long glfwWindowHandle, long nativeGameWindow) {
            this.glfwWindowHandle = glfwWindowHandle;
            this.nativeGameWindow = nativeGameWindow;
        }

        public long glfwWindowHandle() {
            return glfwWindowHandle;
        }

        private long nativeGameWindow() {
            return nativeGameWindow;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            restoreGameInputState();
        }
    }

    private record WindowsOwnerBinding(HWND gameWindow, boolean gameWindowWasEnabled) implements AutoCloseable {
        private static WindowsOwnerBinding none() {
            return new WindowsOwnerBinding(null, false);
        }

        private static WindowsOwnerBinding attach(Frame owner, long nativeGameWindow) {
            if (nativeGameWindow == 0L) {
                return none();
            }
            try {
                HWND gameWindow = new HWND(Pointer.createConstant(nativeGameWindow));
                HWND ownerWindow = new HWND(Native.getWindowPointer(owner));
                RECT gameBounds = new RECT();
                if (ModalUser32.INSTANCE.GetWindowRect(gameWindow, gameBounds)) {
                    int centerX = gameBounds.left + Math.max(0, gameBounds.right - gameBounds.left) / 2;
                    int centerY = gameBounds.top + Math.max(0, gameBounds.bottom - gameBounds.top) / 2;
                    owner.setLocation(centerX, centerY);
                }
                ModalUser32.INSTANCE.SetWindowLongPtr(
                        ownerWindow,
                        WinUser.GWL_HWNDPARENT,
                        gameWindow.getPointer());
                boolean wasEnabled = ModalUser32.INSTANCE.IsWindowEnabled(gameWindow);
                if (wasEnabled) {
                    ModalUser32.INSTANCE.EnableWindow(gameWindow, false);
                }
                return new WindowsOwnerBinding(gameWindow, wasEnabled);
            } catch (Throwable throwable) {
                ChatUpgrade.LOGGER.warn("ChatUpgrade: failed to bind FileDialog owner: {}", throwable.toString());
                return none();
            }
        }

        private void bringDialogToFront(FileDialog dialog) {
            if (gameWindow == null) {
                return;
            }
            try {
                HWND dialogWindow = new HWND(Native.getWindowPointer(dialog));
                ModalUser32.INSTANCE.SetForegroundWindow(dialogWindow);
                ModalUser32.INSTANCE.BringWindowToTop(dialogWindow);
            } catch (Throwable throwable) {
                ChatUpgrade.LOGGER.debug("ChatUpgrade: failed to focus FileDialog: {}", throwable.toString());
            }
        }

        @Override
        public void close() {
            if (gameWindow == null) {
                return;
            }
            try {
                if (gameWindowWasEnabled) {
                    ModalUser32.INSTANCE.EnableWindow(gameWindow, true);
                }
                ModalUser32.INSTANCE.SetForegroundWindow(gameWindow);
            } catch (Throwable throwable) {
                ChatUpgrade.LOGGER.warn("ChatUpgrade: failed to restore game window: {}", throwable.toString());
            }
        }
    }

    private interface ModalUser32 extends User32 {
        ModalUser32 INSTANCE = Native.load("user32", ModalUser32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean EnableWindow(HWND window, boolean enabled);
    }
}