package chatsgate.slackfx;

import javafx.application.Platform;

import module java.base;

/// JavaFX starts only when first wanted (a write-approval dialog, or the chat window), so a tray-only slackfx that
/// nobody looks at never loads it. After startup, work is handed to the FX thread with runLater.
public final class Fx {

    static final AtomicBoolean started = new AtomicBoolean();

    private Fx() { }

    public static void run(Runnable r) {
        if (started.compareAndSet(false, true)) Platform.startup(() -> { Platform.setImplicitExit(false); r.run(); });
        else Platform.runLater(r);
    }

    /// Runs `c` on the FX thread and blocks until it returns, so a server request thread can show a dialog and
    /// wait for the yes/no. Must not be called from the FX thread itself.
    public static <T> T call(java.util.concurrent.Callable<T> c) {
        var box = new CompletableFuture<T>();
        run(() -> { try { box.complete(c.call()); } catch (Throwable t) { box.completeExceptionally(t); } });
        try {
            return box.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
