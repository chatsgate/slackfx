package chatsgate.slackfx;

import chatsgate.slackfx.core.Out;
import chatsgate.slackfx.core.Store;
import jarrunner.jr.apputils.UpdateResult;
import jarrunner.jr.apputils.Updater;

import module java.base;

/// The command line's update notice: at most one check a day (jr's own cache), and the one-line notice on stderr at
/// most once a day, so a script that runs slackfx a hundred times is not told a hundred times.
final class CliUpdates {

    static final Duration DAY = Duration.ofDays(1);
    static volatile String notice;

    private CliUpdates() { }

    static Thread start() {
        var t = new Thread(() -> {
            try {
                var r = Updater.checkAtMostEvery(DAY);
                if (Newer.is(r)) notice = Updater.notice(r, "update");
            } catch (RuntimeException e) {
                // a check must never break a command
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /// Waits a moment for a check that is still running (the day's first command), then prints the notice if it is due.
    static void show(Thread check) {
        if (check == null) return;
        try {
            check.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var n = notice;
        if (n == null || !due()) return;
        Out.err(n);
        try {
            Files.writeString(marker(), Instant.now().toString());
        } catch (IOException e) {
            // no marker only means the next command says it again
        }
    }

    static Path marker() { return Store.home().resolve("run").resolve("update-notified"); }

    static boolean due() {
        try {
            return Files.getLastModifiedTime(marker()).toInstant().plus(DAY).isBefore(Instant.now());
        } catch (IOException e) {
            return true;
        }
    }
}
