package chatsgate.slackfx;

import chatsgate.slackfx.core.AppLink;
import jarrunner.jr.apputils.JrApp;
import jarrunner.jr.apputils.UpdateResult;
import jarrunner.jr.apputils.Updater;
import leasegate.frame.GateFrame;

import module java.base;

/// The client's own update check: shortly after it starts and then every 24 hours it asks jr whether a newer release
/// exists and, if so, asks the person (a window that no stray key can answer: F2 updates, Esc leaves it for the next
/// check). Nothing is downloaded or replaced without that answer. Updating restarts the client in the tray.
final class UiUpdates {

    private UiUpdates() { }

    static void start() {
        var ex = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "update-check");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(UiUpdates::tick, 20, Duration.ofDays(1).toSeconds(), TimeUnit.SECONDS);
    }

    static void tick() {
        try {
            var r = Updater.checkAtMostEvery(Duration.ofHours(23));
            Log.info("update check: " + r.status() + " " + r.message());
            if (!Newer.is(r)) return;
            var yes = Confirm.ask("slackfx " + r.latest() + " is available",
                "This is slackfx " + r.version() + ". Update now? slackfx restarts in the tray to finish.", "Update now", "Later");
            if (yes) install();
        } catch (RuntimeException e) {
            Log.error("update check failed", e);
        }
    }

    static void install() {
        var r = Updater.run(true, true);
        Log.info("update: " + r.status() + " " + r.message());
        if (r.status() == UpdateResult.Status.UPDATED || r.status() == UpdateResult.Status.CURRENT) restart();
        else Fx.run(() -> GateFrame.tell(Look.BRAND, "slackfx could not update", String.valueOf(r.message())));
    }

    /// The old instance must free its place first: a new one that finds a live app.json only asks it to show its window.
    static void restart() {
        var exe = JrApp.exe();
        if (exe.isEmpty()) return;
        try {
            AppLink.delete();
            new ProcessBuilder(exe.get().toString(), "--background").start();
            System.exit(0);
        } catch (IOException e) {
            Log.error("could not start the new slackfx", e);
        }
    }
}
